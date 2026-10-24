package com.marketplace.auth_service.perfil.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.perfil.application.port.in.CambioCorreoPort;
import com.marketplace.auth_service.perfil.application.service.CambioCorreoService;
import com.marketplace.auth_service.perfil.application.service.DoblesCambioCorreo;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

/**
 * {@code POST /api/v1/usuarios/{id}/correo} con el filtro de acceso real (FEAT-04-T3, parte HTTP).
 * El servicio es el real; los puertos de salida son dobles en memoria, así que aquí se prueban
 * contrato, titularidad, validación y que ningún secreto vuelva en la respuesta.
 */
@SpringBootTest(classes = CambioCorreoHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CambioCorreoHttpTest {

    private static final String ISSUER = "https://seguridad.ejemplo.test/api/v1/auth";
    private static final UUID TITULAR = UUID.randomUUID();
    private static final List<String> ORDEN = new ArrayList<>();
    private static final DoblesCambioCorreo.Credenciales CREDENCIALES = new DoblesCambioCorreo.Credenciales(ORDEN);
    private static final DoblesCambioCorreo.Cuentas CUENTAS = new DoblesCambioCorreo.Cuentas(ORDEN, TITULAR);
    private static final DoblesCambioCorreo.Notificaciones NOTIFICACIONES = new DoblesCambioCorreo.Notificaciones(ORDEN);
    private static final DoblesCambioCorreo.Efectos EFECTOS = new DoblesCambioCorreo.Efectos(ORDEN);

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, CambioCorreoController.class,
            CambioCorreoErroresAdvice.class})
    static class Aplicacion {
        @Bean
        CambioCorreoPort cambioCorreoPort() {
            return new CambioCorreoService(CREDENCIALES, CUENTAS, NOTIFICACIONES, EFECTOS);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("identidad.issuer", () -> ISSUER);
        registro.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
        registro.add("spring.mvc.problemdetails.enabled", () -> "true");
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private FirmaPort firmaPort;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void reiniciar() {
        ORDEN.clear();
        CREDENCIALES.contrasenaCorrecta = true;
        CREDENCIALES.revisionVigente = true;
        CUENTAS.correoActual = "actual@correo.com";
        CUENTAS.enUso.clear();
        CUENTAS.existe = true;
        NOTIFICACIONES.confirmaciones.clear();
        NOTIFICACIONES.avisos.clear();
    }

    private HttpResponse<String> solicitar(UUID id, String cuerpo, String bearer)
            throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/usuarios/" + id + "/correo"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (bearer != null) peticion.header("Authorization", "Bearer " + bearer);
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String token(UUID sub) {
        return firmaPort.firmar(new ClaimsAcceso(sub, "ana@ejemplo.test", List.of("CLIENTE"), List.of())).token();
    }

    private static String cuerpo(String nuevoCorreo, String contrasena) {
        return "{\"nuevoCorreo\":\"" + nuevoCorreo + "\",\"contrasenaActual\":\"" + contrasena + "\"}";
    }

    private JsonNode problema(HttpResponse<String> respuesta) throws IOException {
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
        return json.readTree(respuesta.body());
    }

    @Test
    void esc163ElTitularRecibe202ySeEnviaElEnlaceALaDireccionNueva() throws Exception {
        HttpResponse<String> respuesta = solicitar(TITULAR, cuerpo("nuevo@correo.com", "Clave-actual-1"), token(TITULAR));

        assertThat(respuesta.statusCode()).isEqualTo(202);
        assertThat(respuesta.body()).isEmpty();
        assertThat(NOTIFICACIONES.confirmaciones).containsExactly("nuevo@correo.com");
        assertThat(NOTIFICACIONES.avisos).containsExactly("actual@correo.com->nuevo@correo.com");
    }

    @Test
    void esc164ContrasenaEquivocadaResponde401SinEnviarEnlaceNiEcoDelSecreto() throws Exception {
        CREDENCIALES.contrasenaCorrecta = false;

        HttpResponse<String> respuesta = solicitar(TITULAR, cuerpo("nuevo@correo.com", "Mala-clave-9"), token(TITULAR));

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("CREDENCIALES_INVALIDAS");
        assertThat(respuesta.body()).doesNotContain("Mala-clave-9").doesNotContain("nuevo@correo.com");
        assertThat(NOTIFICACIONES.confirmaciones).isEmpty();
    }

    @Test
    void correoYaRegistradoResponde409ConElMensajeNeutroDelContrato() throws Exception {
        CUENTAS.enUso.add("ocupado@correo.com");

        HttpResponse<String> respuesta = solicitar(TITULAR, cuerpo("ocupado@correo.com", "Clave-actual-1"), token(TITULAR));

        assertThat(respuesta.statusCode()).isEqualTo(409);
        JsonNode p = problema(respuesta);
        assertThat(p.get("code").asText()).isEqualTo("CORREO_NO_DISPONIBLE");
        assertThat(p.get("title").asText()).isEqualTo("No se pudo usar ese correo");
        assertThat(respuesta.body()).doesNotContain("ocupado@correo.com");
    }

    @Test
    void esc165OtraCuentaRecibe403YNoSeVerificaNingunaContrasena() throws Exception {
        UUID otro = UUID.randomUUID();

        HttpResponse<String> respuesta = solicitar(TITULAR, cuerpo("nuevo@correo.com", "Clave-actual-1"), token(otro));

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(ORDEN).isEmpty();
        assertThat(NOTIFICACIONES.confirmaciones).isEmpty();
    }

    @Test
    void sinTokenResponde401() throws Exception {
        HttpResponse<String> respuesta = solicitar(TITULAR, cuerpo("nuevo@correo.com", "Clave-actual-1"), null);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(NOTIFICACIONES.confirmaciones).isEmpty();
    }

    @Test
    void camposFaltantesOCorreoInvalidoResponden400SinEco() throws Exception {
        for (String malo : List.of(
                "{\"contrasenaActual\":\"Clave-actual-1\"}",
                "{\"nuevoCorreo\":\"nuevo@correo.com\"}",
                cuerpo("no-es-un-correo", "Clave-actual-1"),
                cuerpo("", "Clave-actual-1"),
                "{no es json")) {
            HttpResponse<String> respuesta = solicitar(TITULAR, malo, token(TITULAR));

            assertThat(respuesta.statusCode()).as(malo).isEqualTo(400);
            assertThat(problema(respuesta).get("code").asText()).isEqualTo("VALIDACION");
            assertThat(respuesta.body()).doesNotContain("Clave-actual-1").doesNotContain("no-es-un-correo");
        }
        assertThat(NOTIFICACIONES.confirmaciones).isEmpty();
    }
}
