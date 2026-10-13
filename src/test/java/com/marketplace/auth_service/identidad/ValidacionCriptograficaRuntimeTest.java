package com.marketplace.auth_service.identidad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.adapter.in.web.DescubrimientoController;
import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEC-IDENTIDAD-T004: validadores probados con firmas reales en el runtime del
 * backend (Java 21, Spring Boot, nimbus-jose-jwt), no con JWT ilustrativos.
 *
 * <p>El backend arranca como en un entorno: clave privada PKCS8 de 3072 bits
 * leída de un <b>archivo</b> ({@code identidad.clave-privada-archivo}) e issuer
 * externo fijo, como detrás de un proxy. Los accesos válidos los emite su
 * {@link FirmaPort}; los inválidos se fabrican aquí con claves de prueba
 * generadas en la ejecución. La comprobación de terceros usa solo el JWKS
 * publicado por HTTP, igual que haría G5. Esto no sustituye la verificación
 * remota con G5 (TEC-IDENTIDAD-T005).
 */
@ExtendWith(OutputCaptureExtension.class)
class ValidacionCriptograficaRuntimeTest {

    private static final String ISSUER = "https://seguridad.ejemplo.test/api/v1/auth";
    private static final String JWKS = "/api/v1/auth/.well-known/jwks.json";
    private static final String PROTEGIDA = "/api/v1/prueba/actor";

    @TempDir
    static Path carpeta;

    private static KeyPair claveDelEntorno;
    private static Path archivoClave;
    private static ConfigurableApplicationContext backend;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, DescubrimientoController.class, ControladorDePrueba.class})
    static class Aplicacion {
    }

    /** Representa cualquier operación de persona protegida; existe solo en esta prueba. */
    @RestController
    static class ControladorDePrueba {

        @GetMapping(PROTEGIDA)
        Map<String, Object> actor(@AuthenticationPrincipal ActorUsuario actor) {
            return Map.of("sub", actor.sub().toString());
        }
    }

    @BeforeAll
    static void arrancarBackend() throws IOException {
        claveDelEntorno = ClavesDePrueba.generar(3072);
        archivoClave = carpeta.resolve("firma.pem");
        Files.writeString(archivoClave, ClavesDePrueba.pemPkcs8(claveDelEntorno), StandardCharsets.US_ASCII);
        backend = arrancar(archivoClave);
    }

    @AfterAll
    static void detenerBackend() {
        if (backend != null) {
            backend.close();
        }
    }

    private static ConfigurableApplicationContext arrancar(Path clave) {
        // Argumentos de línea de comandos: tienen prioridad sobre application.properties,
        // igual que las variables AUTH_* del entorno desplegado.
        return new SpringApplicationBuilder(Aplicacion.class)
                .run("--server.port=0", "--spring.main.banner-mode=off",
                        "--identidad.issuer=" + ISSUER,
                        "--identidad.clave-privada=",
                        "--identidad.clave-privada-archivo=" + clave.toAbsolutePath());
    }

    private static String base(ConfigurableApplicationContext contexto) {
        return "http://localhost:" + ((ServletWebServerApplicationContext) contexto).getWebServer().getPort();
    }

    private static AccesoFirmado emitir(ConfigurableApplicationContext contexto, UUID usuario,
                                        List<String> roles, List<String> permisos) {
        return contexto.getBean(FirmaPort.class).firmar(new ClaimsAcceso(usuario, "t4@ejemplo.test", roles, permisos));
    }

    private static String kidDelEntorno() {
        return TokensDePrueba.kidDe(claveDelEntorno);
    }

    /** Claims de acceso válidos para este entorno, para estropearlos uno a uno. */
    private static JWTClaimsSet.Builder claimsValidos() {
        return TokensDePrueba.accesoValido(ISSUER);
    }

    private static String firmarConLaClaveDelEntorno(JWSAlgorithm alg, String kid, JWTClaimsSet claims) {
        return TokensDePrueba.firmarRsa(claveDelEntorno, alg, kid, claims);
    }

    // ── El acceso emitido ─────────────────────────────────────

    @Test
    void elAccesoEmitidoTieneElPerfilDelContrato() throws Exception {
        UUID usuario = UUID.randomUUID();
        AccesoFirmado acceso = emitir(backend, usuario, List.of("CLIENTE", "VENDEDOR"), List.of());

        SignedJWT jwt = SignedJWT.parse(acceso.token());
        JWSHeader cabecera = jwt.getHeader();
        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        assertThat(cabecera.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(cabecera.getType()).isEqualTo(JOSEObjectType.JWT);
        assertThat(cabecera.getKeyID()).isEqualTo(kidDelEntorno());
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getSubject()).isEqualTo(usuario.toString());
        assertThat(claims.getStringClaim("email")).isEqualTo("t4@ejemplo.test");
        assertThat(claims.getStringClaim("tipo")).isEqualTo("acceso");
        assertThat(claims.getStringListClaim("roles")).containsExactly("CLIENTE", "VENDEDOR");
        assertThat(claims.getStringListClaim("permisos")).as("array vacío, no ausente").isEmpty();
        assertThat(claims.getClaim("permisos")).isInstanceOf(List.class);
        assertThat(UUID.fromString(claims.getJWTID())).isEqualTo(acceso.jti());
        assertThat(claims.getExpirationTime().toInstant())
                .isEqualTo(claims.getIssueTime().toInstant().plusSeconds(900));
        assertThat(claims.getAudience()).as("sin aud de servicio").isEmpty();
        assertThat(claims.getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "sub", "email", "tipo", "roles", "permisos", "iat", "exp", "jti");
    }

    @Test
    void unTerceroLoVerificaSoloConElJwksPublicado() throws Exception {
        AccesoFirmado acceso = emitir(backend, UUID.randomUUID(), List.of("CLIENTE"), List.of());

        HttpResponse<String> respuesta = get(base(backend), JWKS, null);
        assertThat(respuesta.statusCode()).isEqualTo(200);
        JWKSet publicado = JWKSet.parse(respuesta.body());
        SignedJWT jwt = SignedJWT.parse(acceso.token());
        RSAKey publica = (RSAKey) publicado.getKeyByKeyId(jwt.getHeader().getKeyID());

        assertThat(publica).as("el kid del token está en el JWKS").isNotNull();
        assertThat(publica.isPrivate()).isFalse();
        assertThat(publica.size()).isEqualTo(3072);
        assertThat(jwt.verify(new RSASSAVerifier(publica))).isTrue();
    }

    @Test
    void elAccesoValidoEntraAlBackendConRolesYPermisosVacios() throws Exception {
        UUID usuario = UUID.randomUUID();
        AccesoFirmado acceso = emitir(backend, usuario, List.of(), List.of());

        HttpResponse<String> respuesta = get(base(backend), PROTEGIDA, "Bearer " + acceso.token());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(json.readTree(respuesta.body()).get("sub").asText()).isEqualTo(usuario.toString());
    }

    // ── Rechazos con firmas reales ────────────────────────────

    @Test
    void cadaTokenInvalidoResponde401TokenInvalido() throws Exception {
        String valido = emitir(backend, UUID.randomUUID(), List.of("CLIENTE"), List.of()).token();
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        KeyPair otraClave = ClavesDePrueba.generar(3072);
        KeyPair clave2048 = ClavesDePrueba.generar(2048);

        Map<String, String> casos = new LinkedHashMap<>();
        casos.put("payload alterado (escala a ADMIN_SISTEMA) con la firma original", payloadAlterado(valido));
        casos.put("firma con otra clave y nuestro kid",
                TokensDePrueba.firmarRsa(otraClave, JWSAlgorithm.RS256, kidDelEntorno(), claimsValidos().build()));
        casos.put("firma con otra clave y su propio kid", TokensDePrueba.firmarRsa(otraClave, JWSAlgorithm.RS256,
                TokensDePrueba.kidDe(otraClave), claimsValidos().build()));
        casos.put("clave RSA 2048", TokensDePrueba.firmarRsa(clave2048, JWSAlgorithm.RS256,
                TokensDePrueba.kidDe(clave2048), claimsValidos().build()));
        casos.put("sin kid", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, null, claimsValidos().build()));
        casos.put("kid desconocido", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, "kid-ajeno", claimsValidos().build()));
        casos.put("RS512 con nuestra clave", firmarConLaClaveDelEntorno(JWSAlgorithm.RS512, kidDelEntorno(),
                claimsValidos().build()));
        casos.put("PS256 con nuestra clave", firmarConLaClaveDelEntorno(JWSAlgorithm.PS256, kidDelEntorno(),
                claimsValidos().build()));
        casos.put("HS256 con la clave pública como secreto", confusionDeAlgoritmo(claimsValidos().build()));
        casos.put("alg none", TokensDePrueba.sinFirma(claimsValidos().build()));
        casos.put("vencido en este segundo (una tolerancia de 60 s lo aceptaría)", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, kidDelEntorno(),
                claimsValidos().issueTime(Date.from(ahora.minusSeconds(900))).expirationTime(Date.from(ahora)).build()));
        casos.put("sin exp", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, kidDelEntorno(),
                claimsValidos().expirationTime(null).build()));
        casos.put("issuer de otro entorno", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, kidDelEntorno(),
                claimsValidos().issuer("https://otro.ejemplo.test/api/v1/auth").build()));
        casos.put("issuer con barra final", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, kidDelEntorno(),
                claimsValidos().issuer(ISSUER + "/").build()));
        casos.put("sub que no es UUID", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, kidDelEntorno(),
                claimsValidos().subject("customer-42").build()));
        casos.put("sin jti", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, kidDelEntorno(),
                claimsValidos().jwtID(null).build()));
        casos.put("roles como texto", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, kidDelEntorno(),
                claimsValidos().claim("roles", "ADMIN_SISTEMA").build()));
        casos.put("sin permisos", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, kidDelEntorno(),
                claimsValidos().claim("permisos", null).build()));
        casos.put("sin tipo", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, kidDelEntorno(),
                claimsValidos().claim("tipo", null).build()));
        casos.put("tipo refresco", firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, kidDelEntorno(),
                claimsValidos().claim("tipo", "refresco").build()));

        for (var caso : casos.entrySet()) {
            HttpResponse<String> respuesta = get(base(backend), PROTEGIDA, "Bearer " + caso.getValue());
            assertThat(respuesta.statusCode()).as(caso.getKey()).isEqualTo(401);
            JsonNode problema = json.readTree(respuesta.body());
            assertThat(problema.get("code").asText()).as(caso.getKey()).isEqualTo("TOKEN_INVALIDO");
            assertThat(respuesta.body()).as("%s: la respuesta no repite el token", caso.getKey())
                    .doesNotContain(caso.getValue());
        }
    }

    @Test
    void unTokenDeServicioFirmadoPorNosotrosResponde403TokenNoAplicable() throws Exception {
        // Fixture aislada (TEC-IDENTIDAD §7): no provisiona clientes de servicio A10.
        String servicio = firmarConLaClaveDelEntorno(JWSAlgorithm.RS256, kidDelEntorno(),
                TokensDePrueba.servicioValido(ISSUER).build());

        HttpResponse<String> respuesta = get(base(backend), PROTEGIDA, "Bearer " + servicio);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(json.readTree(respuesta.body()).get("code").asText()).isEqualTo("TOKEN_NO_APLICABLE");
    }

    // ── Reinicio ──────────────────────────────────────────────

    @Test
    void trasReiniciarConLaMismaClaveElKidSeConservaYLosAccesosEmitidosSiguenValidos() throws Exception {
        ConfigurableApplicationContext primero = arrancar(archivoClave);
        String kidAntes;
        String emitidoAntes;
        try {
            kidAntes = kidPublicado(primero);
            emitidoAntes = emitir(primero, UUID.randomUUID(), List.of("CLIENTE"), List.of()).token();
        } finally {
            primero.close();
        }

        ConfigurableApplicationContext reiniciado = arrancar(archivoClave);
        try {
            assertThat(kidPublicado(reiniciado)).isEqualTo(kidAntes).isEqualTo(kidDelEntorno());
            assertThat(get(base(reiniciado), PROTEGIDA, "Bearer " + emitidoAntes).statusCode()).isEqualTo(200);
        } finally {
            reiniciado.close();
        }
    }

    @Test
    void unEntornoConOtraClaveNoAceptaLosAccesosDeEste() throws Exception {
        Path otraClave = carpeta.resolve("otra.pem");
        Files.writeString(otraClave, ClavesDePrueba.pemPkcs8(ClavesDePrueba.generar(3072)), StandardCharsets.US_ASCII);
        String emitido = emitir(backend, UUID.randomUUID(), List.of("CLIENTE"), List.of()).token();

        ConfigurableApplicationContext otro = arrancar(otraClave);
        try {
            assertThat(kidPublicado(otro)).isNotEqualTo(kidDelEntorno());
            assertThat(get(base(otro), PROTEGIDA, "Bearer " + emitido).statusCode()).isEqualTo(401);
        } finally {
            otro.close();
        }
    }

    // ── Nada sensible en logs ─────────────────────────────────

    @Test
    void niLaClavePrivadaNiLosTokensAparecenEnLosLogs(CapturedOutput salida) throws Exception {
        String token = emitir(backend, UUID.randomUUID(), List.of("CLIENTE"), List.of()).token();
        get(base(backend), PROTEGIDA, "Bearer " + token);
        get(base(backend), PROTEGIDA, "Bearer " + payloadAlterado(token));

        String pem = ClavesDePrueba.pemPkcs8(claveDelEntorno);
        String cuerpoPem = pem.lines().filter(l -> !l.startsWith("-----")).findFirst().orElseThrow();
        assertThat(salida.getAll())
                .doesNotContain("PRIVATE KEY")
                .doesNotContain(cuerpoPem)
                .doesNotContain(token)
                .doesNotContain(token.split("\\.")[2]);
    }

    // ── Auxiliares ────────────────────────────────────────────

    private String kidPublicado(ConfigurableApplicationContext contexto) throws Exception {
        return JWKSet.parse(get(base(contexto), JWKS, null).body()).getKeys().get(0).getKeyID();
    }

    /** Cambia el payload de un acceso real para que diga ADMIN_SISTEMA y conserva cabecera y firma. */
    private static String payloadAlterado(String token) throws Exception {
        String[] partes = token.split("\\.");
        JWTClaimsSet original = JWTClaimsSet.parse(new Base64URL(partes[1]).decodeToString());
        JWTClaimsSet escalado = new JWTClaimsSet.Builder(original)
                .claim("roles", List.of("ADMIN_SISTEMA"))
                .claim("permisos", List.of("rol.asignar", "rol.revocar"))
                .build();
        return partes[0] + "." + Base64URL.encode(escalado.toString()) + "." + partes[2];
    }

    /** El atacante firma HS256 usando la clave pública (que es pública) como secreto HMAC. */
    private static String confusionDeAlgoritmo(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(kidDelEntorno()).build(), claims);
        jwt.sign(new MACSigner(claveDelEntorno.getPublic().getEncoded()));
        assertThat(JWSObject.parse(jwt.serialize()).getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        return jwt.serialize();
    }

    private HttpResponse<String> get(String base, String ruta, String autorizacion)
            throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(base + ruta)).GET();
        if (autorizacion != null) {
            peticion.header("Authorization", autorizacion);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}
