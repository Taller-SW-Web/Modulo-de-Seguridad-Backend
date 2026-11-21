package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.adapter.in.web.DescubrimientoController;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Registro de peticiones (docs/operacion/LOGS.md) sobre un servidor HTTP real con la cadena de
 * seguridad de producción: número de seguimiento, una línea por petición con su responsable y
 * ningún secreto en el log.
 */
@SpringBootTest(classes = RegistroPeticionesHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ExtendWith(OutputCaptureExtension.class)
class RegistroPeticionesHttpTest {

    private static final int PUERTO = puertoLibre();
    private static final String BASE = "http://localhost:" + PUERTO;
    private static final String SECRETO = "S3cr3t0-que-no-debe-salir";
    private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.firma-que-no-debe-salir";
    private static final String CONTRASENA = "Contrasena-que-no-debe-salir-1";

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, CorsConfig.class, RegistroPeticionesConfig.class,
            DescubrimientoController.class, Rutas.class})
    static class Aplicacion { }

    /** Rutas públicas de la cadena real, con respuestas fijas para la prueba. */
    @RestController
    static class Rutas {

        @PostMapping(path = "/api/v1/auth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        ResponseEntity<ProblemDetail> token(@RequestParam(name = "client_id", required = false) String clientId,
                                            @RequestParam(name = "client_secret", required = false) String secreto) {
            ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos de entrada inválidos", null);
            problema.setProperty("errores", List.of(Map.of("campo", "grant_type", "mensaje", "Debe ser client_credentials.")));
            return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
        }

        @PostMapping("/api/v1/auth/login")
        ResponseEntity<ProblemDetail> login() {
            return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE", "Servicio no disponible", null);
        }

        @GetMapping("/api/v1/password/politica")
        String politica() {
            throw new IllegalStateException("falla de prueba");
        }

        @PostMapping("/api/v1/password/recuperar")
        CompletionStage<ResponseEntity<Void>> recuperar() {
            return CompletableFuture.supplyAsync(() -> ResponseEntity.accepted().build(),
                    CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS));
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("server.port", () -> PUERTO);
        registro.add("identidad.issuer", () -> BASE + "/api/v1/auth");
        registro.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void unaPeticionCorrectaDevuelveSuNumeroYDejaUnaLineaOk(CapturedOutput salida) throws Exception {
        var respuesta = enviar(HttpRequest.newBuilder(uri("/api/v1/auth/.well-known/jwks.json")).GET());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        String id = respuesta.headers().firstValue("X-Request-Id").orElseThrow();
        assertThat(id).matches("[0-9a-f]{16}");
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(salida.getOut()).contains(
                "anónimo | GET /api/v1/auth/.well-known/jwks.json | 200 | correcto | "));
        assertThat(lineaDe(salida, id)).endsWith("| id=" + id + " | [OK]");
    }

    @Test
    void unTokenRechazadoEsDelClienteYNoSeEscribe(CapturedOutput salida) throws Exception {
        String usuario = UUID.randomUUID().toString();
        var respuesta = enviar(HttpRequest.newBuilder(uri("/api/v1/usuarios/" + usuario)).GET()
                .header("Authorization", "Bearer " + TOKEN));

        assertThat(respuesta.statusCode()).isEqualTo(401);
        String id = respuesta.headers().firstValue("X-Request-Id").orElseThrow();
        assertThat(respuesta.body()).contains("\"instance\":\"urn:g7:peticion:" + id + "\"");
        String linea = lineaDe(salida, id);
        assertThat(linea).contains("token no aceptado | GET /api/v1/usuarios/{id} | 401 TOKEN_INVALIDO | ")
                .contains("WARN").endsWith("[CLIENTE]");
        assertThat(salida.getOut()).doesNotContain(usuario).doesNotContain(TOKEN).doesNotContain("firma-que-no-debe-salir");
    }

    @Test
    void elNumeroDeOtroModuloSeReutilizaYUnoInseguroSeReemplaza(CapturedOutput salida) throws Exception {
        var propio = enviar(HttpRequest.newBuilder(uri("/api/v1/auth/.well-known/jwks.json")).GET()
                .header("X-Request-Id", "g1-pedido-000123"));
        var inseguro = enviar(HttpRequest.newBuilder(uri("/api/v1/auth/.well-known/jwks.json")).GET()
                .header("X-Request-Id", "x | [NOSOTROS] inventado"));

        assertThat(propio.headers().firstValue("X-Request-Id")).hasValue("g1-pedido-000123");
        assertThat(inseguro.headers().firstValue("X-Request-Id").orElseThrow()).matches("[0-9a-f]{16}");
        lineaDe(salida, "g1-pedido-000123");
        assertThat(salida.getOut()).doesNotContain("inventado");
    }

    @Test
    void enElTokenDeServicioSeVeElModuloPeroNuncaSuSecreto(CapturedOutput salida) throws Exception {
        var respuesta = enviar(HttpRequest.newBuilder(uri("/api/v1/auth/token"))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=password&client_id=modulo-ventas&client_secret="
                        + SECRETO)));

        assertThat(respuesta.statusCode()).isEqualTo(400);
        String linea = lineaDe(salida, respuesta.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(linea).contains("modulo-ventas (pide token) | POST /api/v1/auth/token | 400 VALIDACION"
                + " | el cuerpo no cumple el contrato: campos grant_type | ").endsWith("[CLIENTE]");
        assertThat(salida.getOut()).doesNotContain(SECRETO).doesNotContain("Debe ser client_credentials");
    }

    @Test
    void unaDependenciaCaidaEsNuestraYNoSeEscribeLaContrasena(CapturedOutput salida) throws Exception {
        var respuesta = enviar(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"correo\":\"ana@correo.test\",\"contrasena\":\""
                        + CONTRASENA + "\"}")));

        assertThat(respuesta.statusCode()).isEqualTo(503);
        String linea = lineaDe(salida, respuesta.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(linea).contains("anónimo | POST /api/v1/auth/login | 503 NO_DISPONIBLE | una dependencia nuestra")
                .contains("ERROR").endsWith("[NOSOTROS]");
        assertThat(salida.getOut()).doesNotContain(CONTRASENA).doesNotContain("ana@correo.test");
    }

    @Test
    void unErrorInesperadoEsNuestro(CapturedOutput salida) throws Exception {
        var respuesta = enviar(HttpRequest.newBuilder(uri("/api/v1/password/politica")).GET());

        // Lo que recibe el cliente lo decide la página de error del contenedor; la línea, en cambio,
        // registra la excepción tal como ocurrió.
        assertThat(respuesta.statusCode()).isGreaterThanOrEqualTo(400);
        String linea = lineaDe(salida, respuesta.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(linea).contains("GET /api/v1/password/politica | 500 | error inesperado en nuestro servicio"
                + " (IllegalStateException)").endsWith("[NOSOTROS]");
    }

    @Test
    void unaRespuestaAsincronaSeRegistraAlTerminar(CapturedOutput salida) throws Exception {
        var respuesta = enviar(HttpRequest.newBuilder(uri("/api/v1/password/recuperar"))
                .POST(HttpRequest.BodyPublishers.noBody()));

        assertThat(respuesta.statusCode()).isEqualTo(202);
        String linea = lineaDe(salida, respuesta.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(linea).contains("POST /api/v1/password/recuperar | 202 | correcto | ").endsWith("[OK]");
    }

    @Test
    void laSaludCorrectaNoLlenaElLog(CapturedOutput salida) throws Exception {
        var respuesta = enviar(HttpRequest.newBuilder(uri("/actuator/health")).GET());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        String id = respuesta.headers().firstValue("X-Request-Id").orElseThrow();
        Thread.sleep(200);
        assertThat(salida.getOut()).doesNotContain("id=" + id);
    }

    /** Espera la línea del registro con ese número y la devuelve. */
    private static String lineaDe(CapturedOutput salida, String id) {
        String[] encontrada = new String[1];
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            encontrada[0] = salida.getOut().lines().filter(l -> l.contains("g7.peticiones"))
                    .filter(l -> l.contains("| id=" + id + " |")).findFirst().orElse(null);
            assertThat(encontrada[0]).as("línea con id=%s", id).isNotNull();
        });
        return encontrada[0];
    }

    private HttpResponse<String> enviar(HttpRequest.Builder peticion) throws Exception {
        return http.send(peticion.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static URI uri(String ruta) {
        return URI.create(BASE + ruta);
    }

    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
