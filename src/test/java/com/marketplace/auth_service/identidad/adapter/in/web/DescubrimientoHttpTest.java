package com.marketplace.auth_service.identidad.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.service.FirmarAccesoService;
import com.marketplace.auth_service.identidad.adapter.out.crypto.ClaveFirmaRsa;
import com.marketplace.auth_service.identidad.adapter.out.crypto.NimbusFirmadorJws;
import com.nimbusds.jose.JWSObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWKS y descubrimiento sobre un servidor HTTP real (TEC-IDENTIDAD-T003).
 *
 * <p>La prueba central hace lo mismo que hará Ventas con Spring Security:
 * configura solo el {@code issuer-uri} ({@link JwtDecoders#fromIssuerLocation}),
 * descubre el JWKS y verifica un acceso firmado por {@link FirmaPort}.
 *
 * <p>Arranca solo identidad y seguridad, sin base de datos ni broker, porque
 * estas rutas no los usan.
 */
@SpringBootTest(classes = DescubrimientoHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class DescubrimientoHttpTest {

    private static final int PUERTO = puertoLibre();
    private static final String BASE = "http://localhost:" + PUERTO;
    private static final String ISSUER = BASE + "/api/v1/auth";

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, DescubrimientoController.class})
    static class Aplicacion {
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

    private AccesoFirmado acceso() {
        return firmaPort.firmar(new ClaimsAcceso(UUID.randomUUID(), "d@ejemplo.com", List.of("CLIENTE"), List.of()));
    }

    @Test
    void unConsumidorSpringSeConfiguraSoloConElIssuerYVerificaElAcceso() {
        AccesoFirmado acceso = acceso();

        JwtDecoder decoderDelConsumidor = JwtDecoders.fromIssuerLocation(ISSUER);

        assertThat(decoderDelConsumidor.decode(acceso.token()).getClaimAsString("tipo")).isEqualTo("acceso");
    }

    @Test
    void elConsumidorRechazaUnTokenFirmadoConOtraClave() {
        ClaveFirmaRsa ajena = ClaveFirmaRsa.desdePem(ClavesDePrueba.pemPkcs8(ClavesDePrueba.generar(3072)));
        String falso = new FirmarAccesoService(new NimbusFirmadorJws(ajena), ISSUER, Clock.systemUTC())
                .firmar(new ClaimsAcceso(UUID.randomUUID(), "x@ejemplo.com", List.of("CLIENTE"), List.of())).token();

        assertThatThrownBy(() -> JwtDecoders.fromIssuerLocation(ISSUER).decode(falso))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void elJwksEsPublicoYSoloLlevaParametrosPublicos() throws Exception {
        HttpResponse<String> respuesta = get("/api/v1/auth/.well-known/jwks.json");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/json"));
        assertThat(respuesta.headers().firstValue("Cache-Control")).contains("max-age=3600, public");

        JsonNode claves = json.readTree(respuesta.body()).get("keys");
        assertThat(claves).hasSize(1);
        JsonNode clave = claves.get(0);
        assertThat(clave.fieldNames()).toIterable().containsExactlyInAnyOrder("kty", "use", "alg", "kid", "n", "e");
        assertThat(clave.get("kty").asText()).isEqualTo("RSA");
        assertThat(clave.get("use").asText()).isEqualTo("sig");
        assertThat(clave.get("alg").asText()).isEqualTo("RS256");
        assertThat(clave.get("kid").asText())
                .isEqualTo(JWSObject.parse(acceso().token()).getHeader().getKeyID());
    }

    @Test
    void elDescubrimientoDeclaraElIssuerYUnJwksUriAbsoluto() throws Exception {
        HttpResponse<String> respuesta = get("/api/v1/auth/.well-known/openid-configuration");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode documento = json.readTree(respuesta.body());
        assertThat(documento.get("issuer").asText()).isEqualTo(ISSUER);
        assertThat(documento.get("jwks_uri").asText()).isEqualTo(ISSUER + "/.well-known/jwks.json");
        assertThat(documento.get("id_token_signing_alg_values_supported")).hasSize(1);
        // H3 no declara endpoints que todavía no existen.
        assertThat(documento.has("token_endpoint")).isFalse();
        assertThat(documento.has("introspection_endpoint")).isFalse();
        assertThat(documento.has("scopes_supported")).isFalse();
    }

    @Test
    void elIssuerDelDescubrimientoEsElIssDelAcceso() throws Exception {
        String issuer = json.readTree(get("/api/v1/auth/.well-known/openid-configuration").body())
                .get("issuer").asText();

        assertThat(JWSObject.parse(acceso().token()).getPayload().toJSONObject().get("iss")).isEqualTo(issuer);
    }

    @Test
    void lasDemasRutasNoSonPublicas() throws Exception {
        assertThat(get("/api/v1/usuarios").statusCode()).isIn(401, 403);
        assertThat(get("/api/v1/auth/.well-known/otra-cosa").statusCode()).isIn(401, 403);
    }

    private HttpResponse<String> get(String ruta) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(BASE + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
