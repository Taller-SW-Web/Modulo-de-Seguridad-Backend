package com.marketplace.auth_service.sesion.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.dto.CierreResultado;
import com.marketplace.auth_service.sesion.application.dto.ContextoSesion;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;import com.marketplace.auth_service.sesion.application.dto.MotivoRechazo;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.dto.UsuarioAutenticado;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.domain.exception.DependenciaNoDisponibleException;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
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
import org.springframework.context.ApplicationContext;
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
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POST {@code /api/v1/auth/refresh} sobre servidor real (EP-02-T004): la ruta
 * es pública, el 200 trae el par contractual y cada rechazo responde el mismo
 * {@code 401 REFRESCO_INVALIDO} sin el secreto. El caso de uso es un doble
 * programable; su lógica se prueba en {@code SesionServiceTest} y PostgreSQL.
 */
@SpringBootTest(classes = SesionHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SesionHttpTest {

    static final AtomicReference<Function<SecretoRefresco, RenovacionResultado>> COMPORTAMIENTO =
            new AtomicReference<>();
    static final AtomicReference<Function<SecretoRefresco, CierreResultado>> COMPORTAMIENTO_CIERRE =
            new AtomicReference<>();
    static final AtomicReference<SecretoRefresco> RECIBIDO = new AtomicReference<>();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, RefrescoController.class, CierreController.class,
            SesionErroresAdvice.class})
    static class Aplicacion {

        @Bean
        SesionesPort sesionesPort() {
            return new SesionesPort() {
                @Override
                public SesionIniciada abrir(UUID u, IdentidadAutorizada i, ContextoPeticion c) {
                    throw new UnsupportedOperationException("fuera de T004");
                }

                @Override
                public ContextoSesion resolverAcceso(AccesoVerificado a) {
                    throw new UnsupportedOperationException("fuera de T004");
                }

                @Override
                public RenovacionResultado renovar(SecretoRefresco secreto) {
                    RECIBIDO.set(secreto);
                    return COMPORTAMIENTO.get().apply(secreto);
                }

                @Override
                public CierreResultado cerrar(SecretoRefresco secreto) {
                    RECIBIDO.set(secreto);
                    return COMPORTAMIENTO_CIERRE.get().apply(secreto);
                }

                @Override
                public void revocarTodas(UUID u) {
                    throw new UnsupportedOperationException("fuera de T004");
                }

                @Override
                public void revocarOtras(UUID u, UUID f) {
                    throw new UnsupportedOperationException("fuera de T004");
                }
            };
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("identidad.issuer", () -> "https://seguridad.ejemplo.test/api/v1/auth");
        registro.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
        registro.add("spring.mvc.problemdetails.enabled", () -> "true");
        registro.add("spring.mvc.problemdetails.enabled", () -> "true");
    }

    @LocalServerPort
    private int puerto;
    @Autowired
    private FirmaPort firmaPort;
    @Autowired
    private ApplicationContext contexto;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final UUID usuario = UUID.randomUUID();

    private SesionIniciada renovada() {
        return new SesionIniciada("acceso-nuevo", "refresco-nuevo", 900,
                Instant.parse("2026-10-12T20:15:00Z"), Instant.parse("2026-10-19T20:00:00Z"),
                new UsuarioAutenticado(usuario, "Cliente Prueba", "c@ejemplo.test", List.of("CLIENTE")));
    }

    @BeforeEach
    void reiniciar() {
        RECIBIDO.set(null);
        COMPORTAMIENTO.set(s -> new RenovacionResultado.Renovada(renovada()));
        COMPORTAMIENTO_CIERRE.set(s -> new CierreResultado.Cerrada(usuario, UUID.randomUUID()));
    }

    private HttpResponse<String> post(String ruta, String cuerpo) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> renovar(String cuerpo) throws IOException, InterruptedException {
        return post("/api/v1/auth/refresh", cuerpo);
    }

    private HttpResponse<String> cerrar(String cuerpo) throws IOException, InterruptedException {
        return post("/api/v1/auth/logout", cuerpo);
    }

    @Test
    void renovacionValidaDevuelveElNuevoPar() throws Exception {
        var respuesta = renovar("{\"refreshToken\":\"secreto-presentado\"}");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("accessToken").asText()).isEqualTo("acceso-nuevo");
        assertThat(cuerpo.get("refreshToken").asText()).isEqualTo("refresco-nuevo");
        assertThat(cuerpo.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(cuerpo.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(cuerpo.get("usuario").get("id").asText()).isEqualTo(usuario.toString());
        assertThat(cuerpo.get("usuario").get("roles").get(0).asText()).isEqualTo("CLIENTE");
        assertThat(RECIBIDO.get().valor()).isEqualTo("secreto-presentado");
        assertThat(RECIBIDO.get().toString()).doesNotContain("secreto-presentado");
    }

    @Test
    void cadaMotivoRespondeElMismo401SinElSecreto() throws Exception {
        for (MotivoRechazo motivo : MotivoRechazo.values()) {
            COMPORTAMIENTO.set(s -> new RenovacionResultado.Rechazada(motivo, usuario, UUID.randomUUID()));
            var respuesta = renovar("{\"refreshToken\":\"secreto-presentado\"}");

            assertThat(respuesta.statusCode()).as("motivo %s", motivo).isEqualTo(401);
            assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                    v -> assertThat(v).startsWith("application/problem+json"));
            JsonNode problema = json.readTree(respuesta.body());
            assertThat(problema.get("code").asText()).isEqualTo("REFRESCO_INVALIDO");
            assertThat(problema.get("title").asText()).isEqualTo("La sesión no es válida");
            assertThat(respuesta.body()).doesNotContain("secreto-presentado");
        }
    }

    @Test
    void consejoRegistrado() {
        assertThat(contexto.getBean(SesionErroresAdvice.class)).isNotNull();
    }

    @Test
    void dependenciaEs503() throws Exception {
        COMPORTAMIENTO.set(s -> {
            throw new DependenciaNoDisponibleException("CuentasPort de Eva");
        });
        var respuesta = renovar("{\"refreshToken\":\"secreto-presentado\"}");

        assertThat(respuesta.statusCode()).isEqualTo(503);
        JsonNode problema = json.readTree(respuesta.body());
        assertThat(problema.get("code").asText()).isEqualTo("NO_DISPONIBLE");
    }

    @Test
    void cuerpoSinRefrescoEs400Validacion() throws Exception {
        for (String cuerpo : new String[]{"{}", "{\"refreshToken\":\"\"}", "{\"refreshToken\":null}"}) {
            var respuesta = renovar(cuerpo);

            assertThat(respuesta.statusCode()).as(cuerpo).isEqualTo(400);
            JsonNode problema = json.readTree(respuesta.body());
            assertThat(problema.get("code").asText()).isEqualTo("VALIDACION");
            assertThat(problema.get("errores").get(0).get("campo").asText()).isEqualTo("refreshToken");
            assertThat(RECIBIDO.get()).isNull();
        }
    }

    @Test
    void cierreNormalYDesconocidoResponden204SinBearer() throws Exception {
        var respuesta = cerrar("{\"refreshToken\":\"secreto-presentado\"}");

        assertThat(respuesta.statusCode()).isEqualTo(204);
        assertThat(respuesta.body()).isEmpty();
        assertThat(RECIBIDO.get().valor()).isEqualTo("secreto-presentado");

        COMPORTAMIENTO_CIERRE.set(s -> new CierreResultado.YaCerrada());
        var desconocido = cerrar("{\"refreshToken\":\"inexistente\"}");

        assertThat(desconocido.statusCode()).isEqualTo(204);
        assertThat(desconocido.body()).isEmpty();
    }

    @Test
    void cierreSinCuerpoEs400Validacion() throws Exception {
        var respuesta = cerrar("{}");

        assertThat(respuesta.statusCode()).isEqualTo(400);
        JsonNode problema = json.readTree(respuesta.body());
        assertThat(problema.get("code").asText()).isEqualTo("VALIDACION");
    }

    @Test
    void cuerpoIlegibleMetodoYRutaDanProblemaSinHtml() throws Exception {
        var ilegible = post("/api/v1/auth/refresh", "no-es-json{");

        assertThat(ilegible.statusCode()).isEqualTo(400);
        assertThat(ilegible.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
        assertThat(json.readTree(ilegible.body()).get("code").asText()).isEqualTo("VALIDACION");

        String bearer = firmaPort.firmar(new ClaimsAcceso(UUID.randomUUID(), "a@ejemplo.test",
                List.of("CLIENTE"), List.of())).token();
        // Las rutas públicas ignoran el Bearer por URI: sin token van al 401
        // del filtro (nunca HTML); con token inexistente van al mapping (404).
        var sinToken = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .GET()
                .build();
        var sinAuth = http.send(sinToken, HttpResponse.BodyHandlers.ofString());
        assertThat(sinAuth.statusCode()).isEqualTo(401);
        assertThat(sinAuth.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));

        var inexistente = HttpRequest.newBuilder(
                URI.create("http://localhost:" + puerto + "/api/v1/auth/no-existe"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        var ruta = http.send(inexistente, HttpResponse.BodyHandlers.ofString());
        assertThat(ruta.statusCode()).isEqualTo(404);
        assertThat(ruta.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
    }
}
