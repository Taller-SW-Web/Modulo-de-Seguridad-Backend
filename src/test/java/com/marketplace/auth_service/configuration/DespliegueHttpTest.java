package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.adapter.in.web.DescubrimientoController;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEC-INFRA-T005 sobre un servidor HTTP real: la plataforma puede comprobar la salud
 * sin token, y la SPA desplegada en otro origen solo pasa CORS si su origen está acordado.
 */
@SpringBootTest(classes = DespliegueHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class DespliegueHttpTest {

    private static final int PUERTO = puertoLibre();
    private static final String BASE = "http://localhost:" + PUERTO;
    private static final String SPA = "https://g7-seguridad.example.test";

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, CorsConfig.class, DescubrimientoController.class})
    static class Aplicacion { }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("server.port", () -> PUERTO);
        registro.add("identidad.issuer", () -> BASE + "/api/v1/auth");
        registro.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
        registro.add("g7.cors.origenes", () -> SPA + ", http://localhost:5173");
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void saludEsPublicaSinDetallesAunqueLlegueUnTokenInvalido() throws Exception {
        var sinToken = get("/actuator/health", null);
        var conBasura = get("/actuator/health", "Bearer no-es-un-jwt");

        assertThat(sinToken.statusCode()).isEqualTo(200);
        assertThat(sinToken.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(conBasura.statusCode()).isEqualTo(200);
    }

    @Test
    void otrosEndpointsDeActuatorNoSePublican() throws Exception {
        assertThat(get("/actuator/env", null).statusCode()).isIn(401, 404);
        assertThat(get("/actuator/beans", null).statusCode()).isIn(401, 404);
    }

    @Test
    void preflightDeLaSpaAcordadaPasaYExponeRetryAfter() throws Exception {
        var respuesta = preflight(SPA, "PATCH");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Origin")).hasValue(SPA);
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Methods").orElse("")).contains("PATCH");
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase())
                .contains("authorization");
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();

        var real = http.send(HttpRequest.newBuilder(URI.create(BASE + "/api/v1/auth/.well-known/jwks.json"))
                .header("Origin", SPA).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(real.headers().firstValue("Access-Control-Allow-Origin")).hasValue(SPA);
        assertThat(real.headers().firstValue("Access-Control-Expose-Headers").orElse("")).contains("Retry-After");
    }

    @Test
    void origenNoAcordadoSeRechazaSinCabecerasCors() throws Exception {
        var respuesta = preflight("https://atacante.example.test", "POST");

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void origenesInvalidosSeRechazanAlArrancar() {
        assertThat(CorsConfig.origenesAcordados("")).isEmpty();
        assertThat(CorsConfig.origenesAcordados(" https://a.test , http://localhost:5173 "))
                .containsExactly("https://a.test", "http://localhost:5173");
        for (String invalido : new String[]{"*", "https://*.vercel.app", "https://a.test/app", "a.test",
                "ftp://a.test", "https://a.test?x=1"}) {
            assertThatThrownBy(() -> CorsConfig.origenesAcordados(invalido))
                    .as(invalido).isInstanceOf(IllegalStateException.class);
        }
    }

    private HttpResponse<String> get(String ruta, String autorizacion) throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create(BASE + ruta)).GET();
        if (autorizacion != null) {
            peticion.header("Authorization", autorizacion);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> preflight(String origen, String metodo) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(BASE + "/api/v1/usuarios/me/atributos"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origen)
                .header("Access-Control-Request-Method", metodo)
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
