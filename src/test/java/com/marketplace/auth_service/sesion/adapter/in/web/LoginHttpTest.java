package com.marketplace.auth_service.sesion.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.sesion.application.dto.DesafioEmitido;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.dto.UsuarioAutenticado;
import com.marketplace.auth_service.sesion.application.port.in.LoginPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * POST {@code /api/v1/auth/login} sobre servidor real (EP-02-T006): ruta pública, forma
 * contractual de {@code SesionIniciada}/{@code DesafioMfa}, el mismo 401 para todo
 * rechazo, 403 de caducidad y 503 si la base cae. El caso de uso es un doble; su lógica
 * está en {@code LoginServiceTest} y la composición real se comprobó contra PostgreSQL.
 */
@SpringBootTest(classes = LoginHttpTest.Aplicacion.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginHttpTest {

    static final AtomicReference<java.util.function.Supplier<ResultadoLogin>> RESULTADO = new AtomicReference<>();
    static final AtomicReference<String> CORREO_RECIBIDO = new AtomicReference<>();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, LoginController.class, SesionErroresAdvice.class})
    static class Aplicacion {
        @Bean
        LoginPort loginPort() {
            return new LoginPort() {
                @Override
                public ResultadoLogin iniciar(String correo, String contrasena,
                                              com.marketplace.auth_service.shared.domain.ContextoPeticion contexto) {
                    CORREO_RECIBIDO.set(correo);
                    return RESULTADO.get().get();
                }

                @Override
                public ResultadoLogin completarMfa(String token, String codigo,
                                                   com.marketplace.auth_service.shared.domain.ContextoPeticion contexto) {
                    CORREO_RECIBIDO.set(codigo);
                    return RESULTADO.get().get();
                }
            };
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("identidad.issuer", () -> "http://localhost/api/v1/auth");
        registro.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }

    @LocalServerPort
    int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private HttpResponse<String> login(String cuerpo, String autorizacion) throws Exception {
        return post("/api/v1/auth/login", cuerpo, autorizacion);
    }

    private HttpResponse<String> post(String ruta, String cuerpo, String autorizacion) throws Exception {
        var peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (autorizacion != null) {
            peticion.header("Authorization", autorizacion);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void sesionIniciadaConLaFormaDelContratoAunqueLlegueUnBearerBasura() throws Exception {
        UUID id = UUID.randomUUID();
        Instant ahora = Instant.now();
        RESULTADO.set(() -> new ResultadoLogin.Iniciada(new SesionIniciada("acceso.jwt", "refresco", 900,
                ahora.plusSeconds(900), ahora.plusSeconds(3600),
                new UsuarioAutenticado(id, "Ana Prueba", "ana@g7.test", List.of("CLIENTE")))));

        var respuesta = login("{\"correo\":\"Ana@G7.test\",\"contrasena\":\"Secreta-2026!\"}", "Bearer basura");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(cuerpo.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(cuerpo.get("usuario").get("id").asText()).isEqualTo(id.toString());
        assertThat(cuerpo.get("usuario").get("roles").get(0).asText()).isEqualTo("CLIENTE");
        assertThat(respuesta.body()).doesNotContain("Secreta-2026!");
        assertThat(CORREO_RECIBIDO.get()).isEqualTo("Ana@G7.test");
    }

    @Test
    void desafioMfaSinTokens() throws Exception {
        RESULTADO.set(() -> new ResultadoLogin.DesafioRequerido(new DesafioEmitido("desafio-opaco", "EMAIL", 300)));

        var respuesta = login("{\"correo\":\"admin@g7.test\",\"contrasena\":\"x\"}", null);

        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(cuerpo.get("mfaRequerido").asBoolean()).isTrue();
        assertThat(cuerpo.get("challengeToken").asText()).isEqualTo("desafio-opaco");
        assertThat(cuerpo.get("expiraEn").asInt()).isEqualTo(300);
        assertThat(cuerpo.has("accessToken")).isFalse();
    }

    @Test
    void rechazoEsElMismo401IncluidoCuerpoVacioOJsonRoto() throws Exception {
        RESULTADO.set(() -> ResultadoLogin.Rechazo.CREDENCIALES_INVALIDAS);

        var conDatos = login("{\"correo\":\"a@g7.test\",\"contrasena\":\"mala\"}", null);
        var vacio = login("{}", null);

        for (var r : List.of(conDatos, vacio)) {
            assertThat(r.statusCode()).isEqualTo(401);
            assertThat(r.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
            assertThat(json.readTree(r.body()).get("code").asText()).isEqualTo("CREDENCIALES_INVALIDAS");
        }
        assertThat(conDatos.body()).doesNotContain("mala");
        assertThat(login("{roto", null).statusCode()).isEqualTo(400);
    }

    @Test
    void caducidadEs403YBaseCaidaEs503() throws Exception {
        RESULTADO.set(() -> ResultadoLogin.Rechazo.CONTRASENA_CADUCADA);
        var caducada = login("{\"correo\":\"g@g7.test\",\"contrasena\":\"x\"}", null);
        assertThat(caducada.statusCode()).isEqualTo(403);
        assertThat(json.readTree(caducada.body()).get("code").asText()).isEqualTo("PASSWORD_CADUCADA");

        RESULTADO.set(() -> {
            throw new DataAccessResourceFailureException("conexión rechazada a 10.0.0.5 con clave secreta");
        });
        var caida = login("{\"correo\":\"g@g7.test\",\"contrasena\":\"x\"}", null);
        assertThat(caida.statusCode()).isEqualTo(503);
        assertThat(json.readTree(caida.body()).get("code").asText()).isEqualTo("NO_DISPONIBLE");
        assertThat(caida.body()).doesNotContain("10.0.0.5", "secreta");
    }

    @Test
    void verificarOtpMapeaCadaResultadoDelContrato() throws Exception {
        String cuerpo = "{\"challengeToken\":\"d\",\"codigo\":\"123456\"}";
        RESULTADO.set(() -> new ResultadoLogin.CodigoInvalido(2));
        var invalido = post("/api/v1/auth/otp/verificar", cuerpo, "Bearer basura");
        assertThat(invalido.statusCode()).isEqualTo(401);
        assertThat(json.readTree(invalido.body()).get("code").asText()).isEqualTo("CODIGO_INVALIDO");
        assertThat(json.readTree(invalido.body()).get("intentosRestantes").asInt()).isEqualTo(2);
        assertThat(invalido.body()).doesNotContain("123456");

        RESULTADO.set(() -> ResultadoLogin.Rechazo.OTP_INTENTOS_AGOTADOS);
        assertThat(json.readTree(post("/api/v1/auth/otp/verificar", cuerpo, null).body()).get("code").asText())
                .isEqualTo("OTP_INTENTOS_AGOTADOS");
        RESULTADO.set(() -> ResultadoLogin.Rechazo.OTP_EXPIRADO);
        assertThat(post("/api/v1/auth/otp/verificar", cuerpo, null).statusCode()).isEqualTo(410);
        RESULTADO.set(() -> ResultadoLogin.Rechazo.DESAFIO_INVALIDO);
        var desafio = post("/api/v1/auth/otp/verificar", cuerpo, null);
        assertThat(desafio.statusCode()).isEqualTo(401);
        assertThat(json.readTree(desafio.body()).get("code").asText()).isEqualTo("TOKEN_INVALIDO");

        CORREO_RECIBIDO.set(null);
        var formato = post("/api/v1/auth/otp/verificar", "{\"challengeToken\":\"d\",\"codigo\":\"12a\"}", null);
        assertThat(formato.statusCode()).isEqualTo(400);
        assertThat(CORREO_RECIBIDO.get()).isNull();

        Instant ahora = Instant.now();
        RESULTADO.set(() -> new ResultadoLogin.Iniciada(new SesionIniciada("acceso.jwt", "refresco", 900,
                ahora.plusSeconds(900), ahora.plusSeconds(3600),
                new UsuarioAutenticado(UUID.randomUUID(), "Admin", "admin@g7.test", List.of("ADMIN_SISTEMA")))));
        var ok = post("/api/v1/auth/otp/verificar", cuerpo, null);
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(json.readTree(ok.body()).get("usuario").get("roles").get(0).asText()).isEqualTo("ADMIN_SISTEMA");
    }
}
