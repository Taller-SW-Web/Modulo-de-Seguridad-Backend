package com.marketplace.auth_service.usuario.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoReenvio;
import com.marketplace.auth_service.usuario.application.dto.ResultadoVerificacion;
import com.marketplace.auth_service.usuario.application.port.in.VerificacionCorreoPort;
import com.marketplace.auth_service.usuario.domain.exception.CuotaNoDisponibleException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/** FEAT-01-T3: contrato HTTP de verificar y reenviar con el caso de uso simulado. */
@SpringBootTest(classes = VerificacionCorreoHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VerificacionCorreoHttpTest {

    /** Decide por el token o el correo y recuerda lo recibido. */
    static final class VerificacionSimulada implements VerificacionCorreoPort {
        final List<String> recibidos = new ArrayList<>();

        @Override
        public ResultadoVerificacion verificar(String tokenClaro, ContextoPeticion contexto) {
            recibidos.add(tokenClaro);
            return switch (tokenClaro) {
                case "vigente" -> ResultadoVerificacion.VERIFICADA;
                case "vencido" -> ResultadoVerificacion.EXPIRADA;
                case "usado" -> ResultadoVerificacion.YA_USADA;
                default -> ResultadoVerificacion.INVALIDA;
            };
        }

        @Override
        public ResultadoReenvio reenviar(String correo, ContextoPeticion contexto) {
            recibidos.add(correo);
            return switch (correo) {
                case "limitado@correo.com" -> new ResultadoReenvio.Limitado(1800);
                case "sinclave@correo.com" -> throw new CuotaNoDisponibleException();
                default -> ResultadoReenvio.Aceptado.ACEPTADO;
            };
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, VerificacionCorreoController.class})
    static class Aplicacion {
        @Bean VerificacionSimulada verificacion() { return new VerificacionSimulada(); }
    }

    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("identidad.issuer", () -> "https://seguridad.example.test/api/v1/auth");
        r.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }

    @LocalServerPort int puerto;
    @Autowired VerificacionSimulada verificacion;
    final HttpClient cliente = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void limpiar() { verificacion.recibidos.clear(); }

    HttpResponse<String> post(String ruta, Object cuerpo, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return cliente.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    String codigo(HttpResponse<String> respuesta) throws Exception {
        return json.readTree(respuesta.body()).path("code").asText();
    }

    @Test void tokenVigenteResponde204AunConBearerInvalido() throws Exception {
        for (String bearer : Arrays.asList(null, "invalido")) {
            var respuesta = post("/api/v1/auth/verificar-correo", Map.of("token", "vigente"), bearer);
            assertThat(respuesta.statusCode()).isEqualTo(204);
            assertThat(respuesta.body()).isEmpty();
        }
    }

    @Test void vencidoUsadoODesconocidoResponden410DelContrato() throws Exception {
        var vencido = post("/api/v1/auth/verificar-correo", Map.of("token", "vencido"), null);
        assertThat(vencido.statusCode()).isEqualTo(410);
        assertThat(codigo(vencido)).isEqualTo("ENLACE_EXPIRADO");
        assertThat(json.readTree(vencido.body()).path("title").asText()).isEqualTo("El enlace de verificación expiró");

        for (String token : List.of("usado", "inventado")) {
            var respuesta = post("/api/v1/auth/verificar-correo", Map.of("token", token), null);
            assertThat(respuesta.statusCode()).isEqualTo(410);
            assertThat(codigo(respuesta)).isEqualTo("ENLACE_YA_USADO");
            assertThat(json.readTree(respuesta.body()).path("type").asText())
                    .isEqualTo("https://g7.unmsm.pe/errores/enlace-ya-usado");
        }
        // El token recibido nunca se devuelve («usado» no sirve: aparece en el propio type).
        assertThat(post("/api/v1/auth/verificar-correo", Map.of("token", "inventado"), null).body())
                .doesNotContain("inventado");
    }

    @Test void sinTokenResponde400SinLlamarAlCasoDeUso() throws Exception {
        for (Object cuerpo : List.of(Map.of(), Map.of("token", "  "))) {
            var respuesta = post("/api/v1/auth/verificar-correo", cuerpo, null);
            assertThat(respuesta.statusCode()).isEqualTo(400);
            assertThat(codigo(respuesta)).isEqualTo("VALIDACION");
        }
        assertThat(verificacion.recibidos).isEmpty();
    }

    @Test void reenvioResponde202ConCuerpoVacioExistaONoLaCuenta() throws Exception {
        var respuesta = post("/api/v1/auth/verificar-correo/reenviar", Map.of("correo", " maria@ejemplo.com "), "x");

        assertThat(respuesta.statusCode()).isEqualTo(202);
        assertThat(respuesta.body()).isEmpty();
        assertThat(verificacion.recibidos).containsExactly("maria@ejemplo.com");
    }

    @Test void cuotaAgotadaResponde429ConRetryAfter() throws Exception {
        var respuesta = post("/api/v1/auth/verificar-correo/reenviar", Map.of("correo", "limitado@correo.com"), null);

        assertThat(respuesta.statusCode()).isEqualTo(429);
        assertThat(respuesta.headers().firstValue("Retry-After")).hasValue("1800");
        assertThat(codigo(respuesta)).isEqualTo("DEMASIADAS_SOLICITUDES");
        assertThat(respuesta.body()).doesNotContain("limitado@correo.com");
    }

    @Test void correoAusenteOInvalidoResponde400() throws Exception {
        for (Object cuerpo : List.of(Map.of(), Map.of("correo", "no-es-correo"))) {
            var respuesta = post("/api/v1/auth/verificar-correo/reenviar", cuerpo, null);
            assertThat(respuesta.statusCode()).isEqualTo(400);
            assertThat(codigo(respuesta)).isEqualTo("VALIDACION");
        }
        assertThat(verificacion.recibidos).isEmpty();
    }

    @Test void sinClaveDeCuotaResponde503NoDisponible() throws Exception {
        var respuesta = post("/api/v1/auth/verificar-correo/reenviar", Map.of("correo", "sinclave@correo.com"), null);

        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(codigo(respuesta)).isEqualTo("NO_DISPONIBLE");
    }
}
