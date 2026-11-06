package com.marketplace.auth_service.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.adapter.in.web.DescubrimientoController;
import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FEAT-12-T1: la configuración por entorno de {@code issuer}, descubrimiento y
 * {@code jwks_uri} (RF-17.1/17.2/17.14, SPEC-17 y H4 T1).
 *
 * <p>El servidor corre en local, pero la configuración es la del entorno
 * desplegado: {@code identidad.issuer} toma la URL pública del entorno desde su
 * variable, nunca {@code localhost}. Así se comprueba que las tres URLs
 * publicadas salen de esa variable y no de la petición, del {@code Host}
 * interno ni del puerto real del proceso, que es lo que exige un consumidor
 * detrás del proxy TLS de la nube.
 *
 * <p>{@code jwks_uri} es absoluto contra la URL pública, así que un tercero lo
 * pediría allí; aquí se lee la misma ruta relativa contra este servidor porque
 * la URL pública no está servida por este proceso de prueba. El JWT se firma
 * con el {@link FirmaPort} real del contexto y se verifica solo con las claves
 * que publica el JWKS: un tercero no necesita nada más.
 */
@SpringBootTest(classes = ConfiguracionEntornoHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class ConfiguracionEntornoHttpTest {

    /** URL pública del entorno desplegado (AUTH_ISSUER de Render), sin '/' final. */
    private static final String ISSUER_PUBLICO = "https://g7-auth-service.onrender.com/api/v1/auth";
    private static final String RUTA_DESCUBRIMIENTO = "/api/v1/auth/.well-known/openid-configuration";
    private static final String RUTA_JWKS = "/api/v1/auth/.well-known/jwks.json";

    private static final int PUERTO = puertoLibre();
    private static final String BASE = "http://localhost:" + PUERTO;
    private static final ObjectMapper JSON = new ObjectMapper();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, CorsConfig.class, DescubrimientoController.class})
    static class Aplicacion { }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("server.port", () -> PUERTO);
        registro.add("identidad.issuer", () -> ISSUER_PUBLICO);
        registro.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
        registro.add("g7.cors.origenes", () -> "https://g7-seguridad.example.test");
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private FirmaPort firmaPort;

    @Test
    void elDescubrimientoDeclaraLasTresUrlsAbsolutasDeLaUrlPublicaDelEntorno() throws Exception {
        JsonNode descubrimiento = descubrimiento();

        assertThat(descubrimiento.path("issuer").asText())
                .as("el issuer publicado es el de la variable del entorno")
                .isEqualTo(ISSUER_PUBLICO);
        assertThat(descubrimiento.path("jwks_uri").asText())
                .isEqualTo(ISSUER_PUBLICO + "/.well-known/jwks.json");
        assertThat(descubrimiento.path("token_endpoint").asText())
                .isEqualTo(ISSUER_PUBLICO + "/token");
        assertThat(descubrimiento.path("introspection_endpoint").asText())
                .isEqualTo(ISSUER_PUBLICO + "/introspeccion");

        for (String campo : List.of("issuer", "jwks_uri", "token_endpoint", "introspection_endpoint")) {
            String url = descubrimiento.path(campo).asText();
            assertThat(url).as(campo).startsWith("https://");
            assertThat(url.toLowerCase())
                    .as(campo + " no puede apuntar al proceso de pruebas")
                    .doesNotContain("localhost", "127.0.0.1");
            assertThat(URI.create(url).getHost())
                    .as(campo + " no puede usar el nombre interno del servicio")
                    .isNotEqualToIgnoringCase("auth-service");
        }
    }

    @Test
    void elIssDelTokenEmitidoCoincideConElDiscoveryYElJwksPublicadoVerificaSuFirma() throws Exception {
        JsonNode descubrimiento = descubrimiento();

        AccesoFirmado firmado = firmaPort.firmar(
                new ClaimsAcceso(UUID.randomUUID(), "prueba@example.test", List.of("CLIENTE"), List.of()));
        SignedJWT token = SignedJWT.parse(firmado.token());

        assertThat(token.getJWTClaimsSet().getIssuer())
                .as("RF-17.14: el iss del token es idéntico al issuer del descubrimiento")
                .isEqualTo(descubrimiento.path("issuer").asText());
        assertThat(descubrimiento.path("issuer").asText()).isEqualTo(ISSUER_PUBLICO);

        String kid = token.getHeader().getKeyID();
        assertThat(kid).as("el token lleva kid").isNotBlank();
        assertThat(get(RUTA_JWKS).statusCode()).isEqualTo(200);

        JWKSet publicado = JWKSet.parse(cuerpo(get(RUTA_JWKS)));
        RSAKey clave = publicado.getKeyByKeyId(kid).toRSAKey();
        assertThat(clave)
                .as("el kid del token está en el JWKS publicado por HTTP")
                .isNotNull();
        assertThat(token.verify(new RSASSAVerifier(clave.toRSAPublicKey())))
                .as("un tercero verifica la firma solo con el JWKS publicado")
                .isTrue();
    }

    private JsonNode descubrimiento() throws Exception {
        var respuesta = get(RUTA_DESCUBRIMIENTO);
        assertThat(respuesta.statusCode())
                .as("el descubrimiento responde sin autenticación")
                .isEqualTo(200);
        return JSON.readTree(respuesta.body());
    }

    private HttpResponse<String> get(String ruta) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(BASE + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String cuerpo(HttpResponse<String> respuesta) {
        return respuesta.body();
    }

    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
