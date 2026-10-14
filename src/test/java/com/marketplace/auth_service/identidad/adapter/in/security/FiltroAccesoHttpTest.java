package com.marketplace.auth_service.identidad.adapter.in.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.autorizacion.adapter.in.web.AutorizacionErroresAdvice;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.TokensDePrueba;
import com.marketplace.auth_service.identidad.adapter.in.web.DescubrimientoController;
import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.nimbusds.jose.JWSAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filtro de acceso sobre un servidor HTTP real (TEC-IDENTIDAD-T002): quién
 * entra, con qué error sale quien no, y que los permisos del token no autorizan.
 *
 * <p>Las rutas {@code /api/v1/prueba/**} existen solo en esta prueba; representan
 * cualquier operación de persona protegida.
 */
@SpringBootTest(classes = FiltroAccesoHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class FiltroAccesoHttpTest {

    private static final int PUERTO = puertoLibre();
    private static final String BASE = "http://localhost:" + PUERTO;
    private static final String ISSUER = BASE + "/api/v1/auth";
    private static final Set<UUID> ADMINISTRADORES = ConcurrentHashMap.newKeySet();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, DescubrimientoController.class,
            AutorizacionErroresAdvice.class, ControladorDePrueba.class})
    static class Aplicacion {

        /** Doble: en la «base» solo son administradores los UUID de ADMINISTRADORES. */
        @Bean
        AutorizacionPort autorizacionPort() {
            return new AutorizacionPort() {
                @Override
                public PermisosResueltos resolver(UUID usuarioId) {
                    return ADMINISTRADORES.contains(usuarioId)
                            ? new PermisosResueltos(Set.of(Rol.ADMIN_SISTEMA), Set.of(Permiso.ROL_ASIGNAR))
                            : PermisosResueltos.ninguno();
                }

                @Override
                public void exigirPermiso(ActorUsuario actor, Permiso permiso) {
                    if (!resolver(actor.sub()).tiene(permiso)) {
                        throw new AccesoDenegadoException(actor.sub(), permiso);
                    }
                }

                @Override
                public void exigirTitularOPrivilegio(ActorUsuario actor, UUID objetivo, Permiso permiso) {
                    if (!actor.esTitularDe(objetivo)) {
                        exigirPermiso(actor, permiso);
                    }
                }

                @Override
                public void bloquearCatalogoAdministrador() {
                }

                @Override
                public Set<UUID> usuariosConRol(Rol rol) {
                    return Set.copyOf(ADMINISTRADORES);
                }
            };
        }
    }

    @RestController
    static class ControladorDePrueba {

        private final AutorizacionPort autorizacion;

        ControladorDePrueba(AutorizacionPort autorizacion) {
            this.autorizacion = autorizacion;
        }

        @GetMapping("/api/v1/prueba/actor")
        Map<String, Object> actor(@AuthenticationPrincipal ActorUsuario actor) {
            return Map.of("sub", actor.sub().toString(), "jti", actor.jti().toString());
        }

        @PostMapping("/api/v1/prueba/roles")
        ResponseEntity<Void> operacionDeAdministrador(@AuthenticationPrincipal ActorUsuario actor) {
            autorizacion.exigirPermiso(actor, Permiso.ROL_ASIGNAR);
            return ResponseEntity.noContent().build();
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("server.port", () -> PUERTO);
        registro.add("identidad.issuer", () -> ISSUER);
        registro.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }

    @Autowired
    private FirmaPort firmaPort;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private AccesoFirmado acceso(UUID usuario, List<String> permisosDelToken) {
        return firmaPort.firmar(new ClaimsAcceso(usuario, "f@ejemplo.com", List.of("CLIENTE"), permisosDelToken));
    }

    // ── Quién entra ───────────────────────────────────────────

    @Test
    void unAccesoValidoEntraYElControladorRecibeElActor() throws Exception {
        UUID usuario = UUID.randomUUID();
        AccesoFirmado acceso = acceso(usuario, List.of());

        HttpResponse<String> respuesta = get("/api/v1/prueba/actor", "Bearer " + acceso.token());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("sub").asText()).isEqualTo(usuario.toString());
        assertThat(cuerpo.get("jti").asText()).isEqualTo(acceso.jti().toString());
        assertThat(respuesta.headers().firstValue("Set-Cookie")).as("stateless: sin sesión").isEmpty();
    }

    // ── 401 TOKEN_INVALIDO ────────────────────────────────────

    @Test
    void sinBearerResponde401TokenInvalidoConElFormatoDelContrato() throws Exception {
        HttpResponse<String> respuesta = get("/api/v1/prueba/actor", null);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(respuesta.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
        JsonNode problema = json.readTree(respuesta.body());
        assertThat(problema.get("type").asText()).isEqualTo("https://g7.unmsm.pe/errores/token-invalido");
        assertThat(problema.get("title").asText()).isEqualTo("Token ausente o inválido");
        assertThat(problema.get("status").asInt()).isEqualTo(401);
        assertThat(problema.get("code").asText()).isEqualTo("TOKEN_INVALIDO");
    }

    @Test
    void tokensInvalidosResponden401TokenInvalido() throws Exception {
        String valido = acceso(UUID.randomUUID(), List.of()).token();
        String[] partes = valido.split("\\.");
        String alterado = partes[0] + "." + partes[1] + "." + new StringBuilder(partes[2]).reverse();
        Instant haceUnSegundo = Instant.now().minusSeconds(1);
        String vencido = TokensDePrueba.firmarRs256(TokensDePrueba.accesoValido(ISSUER)
                .issueTime(Date.from(haceUnSegundo.minusSeconds(900)))
                .expirationTime(Date.from(haceUnSegundo)).build());
        String otroIssuer = TokensDePrueba.firmarRs256(
                TokensDePrueba.accesoValido("https://otro.example.test/api/v1/auth").build());
        String otroKid = TokensDePrueba.firmarRsa(ClavesDePrueba.RSA_3072, JWSAlgorithm.RS256, "otro",
                TokensDePrueba.accesoValido(ISSUER).build());
        String refresco = TokensDePrueba.firmarRs256(TokensDePrueba.accesoValido(ISSUER).claim("tipo", "refresco").build());
        String sinFirma = TokensDePrueba.sinFirma(TokensDePrueba.accesoValido(ISSUER).build());

        Map<String, String> casos = Map.of(
                "mal formado", "Bearer abc",
                "firma alterada", "Bearer " + alterado,
                "vencido", "Bearer " + vencido,
                "issuer ajeno", "Bearer " + otroIssuer,
                "kid ajeno", "Bearer " + otroKid,
                "perfil refresco", "Bearer " + refresco,
                "alg none", "Bearer " + sinFirma,
                "esquema Basic", "Basic dXN1YXJpbzpjbGF2ZQ==");

        for (var caso : casos.entrySet()) {
            HttpResponse<String> respuesta = get("/api/v1/prueba/actor", caso.getValue());
            assertThat(respuesta.statusCode()).as(caso.getKey()).isEqualTo(401);
            assertThat(json.readTree(respuesta.body()).get("code").asText()).as(caso.getKey()).isEqualTo("TOKEN_INVALIDO");
        }
    }

    @Test
    void elTokenNoSeAceptaEnLaUrlNiEnUnaCookie() throws Exception {
        String token = acceso(UUID.randomUUID(), List.of()).token();

        assertThat(get("/api/v1/prueba/actor?access_token=" + token, null).statusCode()).isEqualTo(401);
        HttpResponse<String> conCookie = http.send(HttpRequest.newBuilder(URI.create(BASE + "/api/v1/prueba/actor"))
                .header("Cookie", "access_token=" + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(conCookie.statusCode()).isEqualTo(401);
    }

    // ── 403 ───────────────────────────────────────────────────

    @Test
    void unTokenDeServicioValidoResponde403TokenNoAplicable() throws Exception {
        String servicio = TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER).build());

        HttpResponse<String> respuesta = get("/api/v1/prueba/actor", "Bearer " + servicio);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        JsonNode problema = json.readTree(respuesta.body());
        assertThat(problema.get("code").asText()).isEqualTo("TOKEN_NO_APLICABLE");
        assertThat(problema.get("title").asText()).isEqualTo("Este endpoint requiere un token de usuario");
    }

    @Test
    void losPermisosDelTokenNoAutorizanLaOperacion() throws Exception {
        // El token dice rol.asignar, pero en la «base» no es administrador.
        String token = acceso(UUID.randomUUID(), List.of("rol.asignar")).token();

        HttpResponse<String> respuesta = post("/api/v1/prueba/roles", "Bearer " + token);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        JsonNode problema = json.readTree(respuesta.body());
        assertThat(problema.get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(problema.get("title").asText()).isEqualTo("Permisos insuficientes");
        assertThat(respuesta.body()).doesNotContain("rol.asignar");
    }

    @Test
    void conElPermisoActualLaOperacionSeHace() throws Exception {
        UUID admin = UUID.randomUUID();
        ADMINISTRADORES.add(admin);
        // El token no lleva permisos (se emitió antes del cambio), pero la base sí.
        String token = acceso(admin, List.of()).token();

        assertThat(post("/api/v1/prueba/roles", "Bearer " + token).statusCode()).isEqualTo(204);
    }

    // ── Rutas públicas y errores ──────────────────────────────

    @Test
    void lasRutasPublicasIgnoranUnBearerInvalido() throws Exception {
        assertThat(get("/api/v1/auth/.well-known/jwks.json", "Bearer basura").statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/auth/.well-known/openid-configuration", "Bearer basura").statusCode()).isEqualTo(200);
    }

    @Test
    void lasRutasDeAuthNoSonPublicasPorComodin() throws Exception {
        assertThat(get("/api/v1/auth/me", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/auth/.well-known/otra", null).statusCode()).isEqualTo(401);
    }

    @Test
    void ningunErrorEsUnaPaginaHtml() throws Exception {
        String token = acceso(UUID.randomUUID(), List.of()).token();

        for (HttpResponse<String> respuesta : List.of(
                get("/api/v1/no-existe", "Bearer " + token),
                get("/api/v1/no-existe", null),
                get("/api/v1/prueba/actor", "Bearer abc"))) {
            assertThat(respuesta.headers().firstValue("Content-Type").orElse(""))
                    .as("%s", respuesta.uri()).doesNotContain("text/html");
            assertThat(respuesta.body()).doesNotContain("<html");
        }
    }

    private HttpResponse<String> get(String ruta, String autorizacion) throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(BASE + ruta))
                .header("Accept", "text/html,application/json").GET();
        if (autorizacion != null) {
            peticion.header("Authorization", autorizacion);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String ruta, String autorizacion) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(BASE + ruta))
                .header("Authorization", autorizacion)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
