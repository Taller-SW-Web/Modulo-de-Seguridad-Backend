package com.marketplace.auth_service.sesion.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.sesion.application.dto.CredencialesLogin;
import com.marketplace.auth_service.sesion.application.dto.DesafioMfaRespuesta;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.dto.UsuarioAutenticado;
import com.marketplace.auth_service.sesion.application.port.in.LoginPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POST {@code /api/v1/auth/login} sobre servidor real (EP-02-T006): la ruta es
 * pública, el 200 trae sesión o desafío, el 401 es idéntico en todas las ramas
 * y el 403 solo caducidad. El coordinador es un doble programable; sus ramas
 * se prueban en {@code LoginServiceTest} y la composición real en T007/T010.
 */
@SpringBootTest(classes = LoginHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginHttpTest {

    static final AtomicReference<BiFunction<CredencialesLogin, ContextoPeticion, ResultadoLogin>> COMPORTAMIENTO =
            new AtomicReference<>();
    static final AtomicReference<CredencialesLogin> RECIBIDO = new AtomicReference<>();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, LoginController.class, SesionErroresAdvice.class})
    static class Aplicacion {

        @Bean
        LoginPort loginPort() {
            return (credenciales, contexto) -> {
                RECIBIDO.set(credenciales);
                return COMPORTAMIENTO.get().apply(credenciales, contexto);
            };
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("identidad.issuer", () -> "https://seguridad.ejemplo.test/api/v1/auth");
        registro.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
        registro.add("spring.mvc.problemdetails.enabled", () -> "true");
    }

    @LocalServerPort
    private int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final UUID usuario = UUID.randomUUID();

    private SesionIniciada sesion() {
        return new SesionIniciada("acceso-1", "refresco-1", 900,
                Instant.parse("2026-10-12T20:15:00Z"), Instant.parse("2026-10-19T20:00:00Z"),
                new UsuarioAutenticado(usuario, "María Quispe Rojas", "maria@ejemplo.com",
                        List.of("CLIENTE")));
    }

    @BeforeEach
    void reiniciar() {
        RECIBIDO.set(null);
        COMPORTAMIENTO.set((c, x) -> new ResultadoLogin.Exito(sesion()));
    }

    private HttpResponse<String> post(String cuerpo) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void exitoDevuelveSesionSinMfa() throws Exception {
        var respuesta = post("{\"correo\":\"maria@ejemplo.com\",\"contrasena\":\"Marketplace2026!\"}");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("accessToken").asText()).isEqualTo("acceso-1");
        assertThat(cuerpo.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(cuerpo.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(cuerpo.get("usuario").get("correo").asText()).isEqualTo("maria@ejemplo.com");
        assertThat(RECIBIDO.get().correo()).isEqualTo("maria@ejemplo.com");
        assertThat(RECIBIDO.get().contrasena().valor()).isEqualTo("Marketplace2026!");
        assertThat(RECIBIDO.get().contrasena().toString()).doesNotContain("Marketplace2026!");
        assertThat(respuesta.body()).doesNotContain("Marketplace2026!");
    }

    @Test
    void desafioDevuelveMfaSinTokens() throws Exception {
        COMPORTAMIENTO.set((c, x) -> new ResultadoLogin.Desafio(
                new DesafioMfaRespuesta(true, "chg_8f2a91c4b7e5", "EMAIL", 300)));
        var respuesta = post("{\"correo\":\"maria@ejemplo.com\",\"contrasena\":\"Marketplace2026!\"}");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("mfaRequerido").asBoolean()).isTrue();
        assertThat(cuerpo.get("challengeToken").asText()).isEqualTo("chg_8f2a91c4b7e5");
        assertThat(cuerpo.get("canal").asText()).isEqualTo("EMAIL");
        assertThat(cuerpo.get("expiraEn").asLong()).isEqualTo(300);
        assertThat(cuerpo.has("accessToken")).isFalse();
    }

    @Test
    void rechazoYCaducidadConCodigosDelContrato() throws Exception {
        COMPORTAMIENTO.set((c, x) -> new ResultadoLogin.Rechazado());
        var noAutorizado = post("{\"correo\":\"maria@ejemplo.com\",\"contrasena\":\"otra\"}");

        assertThat(noAutorizado.statusCode()).isEqualTo(401);
        assertThat(noAutorizado.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
        JsonNode problema = json.readTree(noAutorizado.body());
        assertThat(problema.get("code").asText()).isEqualTo("CREDENCIALES_INVALIDAS");
        assertThat(problema.get("title").asText()).isEqualTo("Credenciales inválidas");
        assertThat(problema.get("detail").asText()).isEqualTo("El correo o la contraseña no son correctos.");
        assertThat(noAutorizado.body()).doesNotContain("otra");

        COMPORTAMIENTO.set((c, x) -> new ResultadoLogin.Caducada());
        var prohibido = post("{\"correo\":\"maria@ejemplo.com\",\"contrasena\":\"otra\"}");

        assertThat(prohibido.statusCode()).isEqualTo(403);
        assertThat(json.readTree(prohibido.body()).get("code").asText()).isEqualTo("PASSWORD_CADUCADA");
    }

    @Test
    void faltantesResponden400ConSuCampo() throws Exception {
        for (String[] caso : new String[][]{
                {"{}", "correo"}, {"{\"correo\":\"\"}", "correo"},
                {"{\"correo\":\"maria@ejemplo.com\"}", "contrasena"}}) {
            var respuesta = post(caso[0]);

            assertThat(respuesta.statusCode()).as(caso[0]).isEqualTo(400);
            JsonNode problema = json.readTree(respuesta.body());
            assertThat(problema.get("code").asText()).isEqualTo("VALIDACION");
            assertThat(problema.get("errores").get(0).get("campo").asText()).isEqualTo(caso[1]);
            assertThat(RECIBIDO.get()).isNull();
        }
    }
}
