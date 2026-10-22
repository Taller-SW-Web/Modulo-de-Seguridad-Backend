package com.marketplace.auth_service.credencial.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.credencial.application.dto.ResultadoSolicitudRecuperacion;
import com.marketplace.auth_service.credencial.application.port.in.SolicitarRecuperacionPort;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

@SpringBootTest(classes = SolicitarRecuperacionHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SolicitarRecuperacionHttpTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, SolicitarRecuperacionController.class})
    static class Aplicacion {
        @Bean ResultadoPrueba resultadoPrueba() { return new ResultadoPrueba(); }
        @Bean SolicitarRecuperacionPort solicitudes(ResultadoPrueba prueba) { return prueba; }
    }

    static final class ResultadoPrueba implements SolicitarRecuperacionPort {
        final AtomicReference<ResultadoSolicitudRecuperacion> resultado = new AtomicReference<>(
                new ResultadoSolicitudRecuperacion.Aceptada());
        @Override public ResultadoSolicitudRecuperacion solicitar(String correo, ContextoPeticion contexto) {
            return resultado.get();
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("identidad.issuer", () -> "https://seguridad.example.test/api/v1/auth");
        r.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }

    @LocalServerPort int puerto;
    @Autowired ObjectMapper json;
    @Autowired @Qualifier("resultadoPrueba") ResultadoPrueba prueba;
    final HttpClient cliente = HttpClient.newHttpClient();

    @Test
    void rutaPublicaDevuelve202VacioParaCorreoValidoAunqueBearerSeaInvalido() throws Exception {
        for (String authorization : new String[]{null, "Bearer no-es-un-token"}) {
            long inicio = System.nanoTime();
            HttpResponse<String> respuesta = solicitar("persona@example.test", authorization);
            long transcurrido = System.nanoTime() - inicio;
            assertThat(respuesta.statusCode()).isEqualTo(202);
            assertThat(respuesta.body()).isEmpty();
            assertThat(transcurrido).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(290));
        }
        assertThat(solicitar("correo-invalido", null).statusCode()).isEqualTo(400);
        assertThat(solicitar("a".repeat(255) + "@example.test", null).statusCode()).isEqualTo(400);
    }

    @Test
    void cuotaAgotadaDevuelve429ConRetryAfterYProblemaPublicado() throws Exception {
        prueba.resultado.set(new ResultadoSolicitudRecuperacion.Limite(42));

        HttpResponse<String> respuesta = solicitar("persona@example.test", null);

        assertThat(respuesta.statusCode()).isEqualTo(429);
        assertThat(respuesta.headers().firstValue("Retry-After")).contains("42");
        assertThat(json.readTree(respuesta.body()).path("code").asText()).isEqualTo("DEMASIADAS_SOLICITUDES");
    }

    private HttpResponse<String> solicitar(String correo, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/password/recuperar"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"" + correo + "\"}"));
        if (bearer != null) builder.header("Authorization", bearer);
        return cliente.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
