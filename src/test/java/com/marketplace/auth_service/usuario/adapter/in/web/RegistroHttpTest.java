package com.marketplace.auth_service.usuario.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.dto.SolicitudRegistro;
import com.marketplace.auth_service.usuario.application.port.in.RegistroClientePort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

/** FEAT-01-T1: contrato HTTP del registro con el caso de uso simulado (validación, códigos y ruta pública). */
@SpringBootTest(classes = RegistroHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistroHttpTest {

    static final UUID CREADA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Rechazo 400 auditado: nombres de campo y canal válido recibido. */
    record Auditada(List<String> campos, String canalOrigen) { }

    /** Decide por el correo (o la contraseña vacía) y recuerda lo recibido. */
    static final class RegistroSimulado implements RegistroClientePort {
        final List<SolicitudRegistro> recibidas = new ArrayList<>();
        final List<Auditada> auditadas = new ArrayList<>();

        @Override
        public ResultadoRegistro registrar(SolicitudRegistro solicitud, ContextoPeticion contexto) {
            recibidas.add(solicitud);
            if (solicitud.contrasena().isEmpty()) {
                return new ResultadoRegistro.PoliticaIncumplida(List.of(
                        new ResultadoRegistro.ReglaIncumplida("LONGITUD_MINIMA", 10)));
            }
            return switch (solicitud.correo()) {
                case "caida@correo.com" -> throw new VerificacionNoDisponibleException(new IllegalStateException("outbox"));
                case "existente@correo.com" -> ResultadoRegistro.Rechazo.CORREO_NO_DISPONIBLE;
                case "debil@correo.com" -> new ResultadoRegistro.PoliticaIncumplida(List.of(
                        new ResultadoRegistro.ReglaIncumplida("LONGITUD_MINIMA", 10),
                        new ResultadoRegistro.ReglaIncumplida("CARACTER_ESPECIAL", null)));
                default -> new ResultadoRegistro.Creada(CREADA);
            };
        }

        @Override
        public void auditarSolicitudInvalida(List<String> campos, String canalOrigen, ContextoPeticion contexto) {
            auditadas.add(new Auditada(List.copyOf(campos), canalOrigen));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, RegistroController.class})
    static class Aplicacion {
        @Bean RegistroSimulado registro() { return new RegistroSimulado(); }
    }

    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("identidad.issuer", () -> "https://seguridad.example.test/api/v1/auth");
        r.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }

    @LocalServerPort int puerto;
    @Autowired RegistroSimulado registro;
    final HttpClient cliente = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void limpiar() {
        registro.recibidas.clear();
        registro.auditadas.clear();
    }

    static Map<String, Object> valido() {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("correo", "maria@ejemplo.com");
        cuerpo.put("contrasena", "Marketplace2026!");
        cuerpo.put("nombres", "María");
        cuerpo.put("apellidos", "Quispe Rojas");
        cuerpo.put("celular", "+51987654321");
        cuerpo.put("aceptaTerminos", true);
        return cuerpo;
    }

    HttpResponse<String> registrar(Object cuerpo, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/auth/registro"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return cliente.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    JsonNode errorDe(HttpResponse<String> respuesta, String campo) throws Exception {
        assertThat(respuesta.statusCode()).isEqualTo(400);
        var cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.path("code").asText()).isEqualTo("VALIDACION");
        for (JsonNode error : cuerpo.path("errores")) {
            if (error.path("campo").asText().equals(campo)) return error;
        }
        throw new AssertionError("Sin error en " + campo + ": " + respuesta.body());
    }

    @Test void creaSinTokensYCanalWebPorDefectoAunConBearerInvalido() throws Exception {
        for (String bearer : java.util.Arrays.asList(null, "invalido")) {
            var respuesta = registrar(valido(), bearer);

            assertThat(respuesta.statusCode()).isEqualTo(201);
            var cuerpo = json.readTree(respuesta.body());
            assertThat(cuerpo.path("id").asText()).isEqualTo(CREADA.toString());
            assertThat(cuerpo.path("estado").asText()).isEqualTo("PENDIENTE_VERIFICACION");
            assertThat(respuesta.body()).doesNotContain("accessToken", "refreshToken", "Marketplace2026!");
        }
        assertThat(registro.recibidas).allSatisfy(s -> assertThat(s.canalOrigen()).isEqualTo("WEB"));
    }

    @Test void respetaElCanalDeOrigenRecibido() throws Exception {
        var cuerpo = valido();
        cuerpo.put("canalOrigen", "MARKETPLACE");

        assertThat(registrar(cuerpo, null).statusCode()).isEqualTo(201);
        assertThat(registro.recibidas.getFirst().canalOrigen()).isEqualTo("MARKETPLACE");
    }

    @Test void sinAceptarTerminosResponde400YNoLlamaAlCasoDeUso() throws Exception {
        var falso = valido();
        falso.put("aceptaTerminos", false);
        var ausente = valido();
        ausente.remove("aceptaTerminos");

        errorDe(registrar(falso, null), "aceptaTerminos");
        errorDe(registrar(ausente, null), "aceptaTerminos");
        assertThat(registro.recibidas).isEmpty();
        assertThat(registro.auditadas).containsExactly(new Auditada(List.of("aceptaTerminos"), "WEB"),
                new Auditada(List.of("aceptaTerminos"), "WEB"));
    }

    @Test void celularNoPeruanoResponde400() throws Exception {
        for (String celular : List.of("987654321", "+1 555 0100")) {
            var cuerpo = valido();
            cuerpo.put("celular", celular);
            errorDe(registrar(cuerpo, null), "celular");
        }
        assertThat(registro.recibidas).isEmpty();
        assertThat(registro.auditadas).hasSize(2).allSatisfy(a -> assertThat(a.campos()).containsExactly("celular"));
    }

    @Test void correoInvalidoCanalDesconocidoYFaltantesResponden400PorCampo() throws Exception {
        var cuerpo = valido();
        cuerpo.put("correo", "no-es-correo");
        cuerpo.put("canalOrigen", "https://evil.example");
        cuerpo.remove("nombres");
        var respuesta = registrar(cuerpo, null);

        errorDe(respuesta, "correo");
        errorDe(respuesta, "canalOrigen");
        errorDe(respuesta, "nombres");
        assertThat(registro.recibidas).isEmpty();
        // El canal inválido no se audita como canal: solo como campo rechazado.
        assertThat(registro.auditadas).containsExactly(
                new Auditada(List.of("correo", "nombres", "canalOrigen"), null));
    }

    @Test void contrasenaVaciaLlegaALaPoliticaY422PeroAusenteEs400Auditado() throws Exception {
        var vacia = valido();
        vacia.put("contrasena", "");
        var respuestaVacia = registrar(vacia, null);

        assertThat(respuestaVacia.statusCode()).isEqualTo(422);
        assertThat(json.readTree(respuestaVacia.body()).path("code").asText()).isEqualTo("POLITICA_INCUMPLIDA");
        assertThat(registro.recibidas).singleElement().satisfies(s -> assertThat(s.contrasena()).isEmpty());
        assertThat(registro.auditadas).isEmpty();

        var ausente = valido();
        ausente.remove("contrasena");
        errorDe(registrar(ausente, null), "contrasena");
        assertThat(registro.recibidas).hasSize(1);
        assertThat(registro.auditadas).containsExactly(new Auditada(List.of("contrasena"), "WEB"));
    }

    @Test void contrasenaCortaNoSeRechazaPorFormaSinoPorPolitica() throws Exception {
        var cuerpo = valido();
        cuerpo.put("correo", "debil@correo.com");
        cuerpo.put("contrasena", "abc");
        var respuesta = registrar(cuerpo, null);

        assertThat(respuesta.statusCode()).isEqualTo(422);
        var problema = json.readTree(respuesta.body());
        assertThat(problema.path("code").asText()).isEqualTo("POLITICA_INCUMPLIDA");
        assertThat(problema.path("errores").toString())
                .isEqualTo("[{\"regla\":\"LONGITUD_MINIMA\",\"esperado\":10},{\"regla\":\"CARACTER_ESPECIAL\"}]");
        assertThat(respuesta.body()).doesNotContain("abc");
    }

    @Test void colaDeCorreoCaidaResponde503NoDisponible() throws Exception {
        var cuerpo = valido();
        cuerpo.put("correo", "caida@correo.com");
        var respuesta = registrar(cuerpo, null);

        assertThat(respuesta.statusCode()).isEqualTo(503);
        var problema = json.readTree(respuesta.body());
        assertThat(problema.path("code").asText()).isEqualTo("NO_DISPONIBLE");
        assertThat(problema.path("title").asText()).isEqualTo("Servicio no disponible");
        assertThat(respuesta.body()).doesNotContain("caida@correo.com", "outbox");
    }

    @Test void correoExistenteResponde409NeutroDelContrato() throws Exception {
        var cuerpo = valido();
        cuerpo.put("correo", "Existente@Correo.com");
        var respuesta = registrar(cuerpo, null);

        assertThat(respuesta.statusCode()).isEqualTo(409);
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                tipo -> assertThat(tipo).startsWith("application/problem+json"));
        var problema = json.readTree(respuesta.body());
        assertThat(problema.path("type").asText()).isEqualTo("https://g7.unmsm.pe/errores/correo-no-disponible");
        assertThat(problema.path("title").asText()).isEqualTo("No se pudo completar el registro");
        assertThat(problema.path("code").asText()).isEqualTo("CORREO_NO_DISPONIBLE");
        assertThat(problema.path("detail").asText())
                .isEqualTo("Si el correo es válido, recibirás un mensaje con los pasos a seguir.");
        assertThat(respuesta.body()).doesNotContain("existente@correo.com");
    }
}
