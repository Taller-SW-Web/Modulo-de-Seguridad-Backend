package com.marketplace.auth_service.credencial.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.*;
import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.credencial.application.port.in.ConsultarPoliticaPort;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = PoliticaPasswordHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PoliticaPasswordHttpTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, PoliticaPasswordController.class})
    static class Aplicacion {
        @Bean ConsultarPoliticaPort politica() { return PoliticaPassword::vigente; }
    }
    @DynamicPropertySource static void propiedades(DynamicPropertyRegistry r) {
        r.add("identidad.issuer", () -> "https://seguridad.example.test/api/v1/auth");
        r.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }
    @LocalServerPort int puerto;
    final HttpClient cliente = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();
    HttpResponse<String> llamar(String metodo, String ruta, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, HttpRequest.BodyPublishers.noBody());
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return cliente.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void getPublicoExactoConBearerAusenteOMalformado() throws Exception {
        for (String bearer : Arrays.asList(null, "invalido")) {
            var respuesta = llamar("GET", "/api/v1/password/politica", bearer);
            assertThat(respuesta.statusCode()).isEqualTo(200);
            var cuerpo = json.readTree(respuesta.body());
            assertThat(cuerpo.size()).isEqualTo(9);
            assertThat(cuerpo.path("longitudMinima").asInt()).isEqualTo(10);
            assertThat(cuerpo.path("historial").asInt()).isEqualTo(5);
            assertThat(cuerpo.path("caducidadDiasAdmin").asInt()).isEqualTo(90);
            assertThat(cuerpo.path("rechazaComunes").asBoolean()).isTrue();
            assertThat(cuerpo.path("requiereMayuscula").asBoolean()).isTrue();
            assertThat(cuerpo.path("requiereMinuscula").asBoolean()).isTrue();
            assertThat(cuerpo.path("requiereDigito").asBoolean()).isTrue();
            assertThat(cuerpo.path("requiereCaracterEspecial").asBoolean()).isTrue();
            assertThat(cuerpo.path("rechazaDatosPersonales").asBoolean()).isTrue();
            assertThat(respuesta.body()).doesNotContain("hash", "lista", "usuarioId");
        }
    }
    @Test void otrosMetodosYRutasNoSeHacenPublicos() throws Exception {
        assertThat(llamar("POST", "/api/v1/password/politica", null).statusCode()).isEqualTo(401);
        assertThat(llamar("GET", "/api/v1/password/cambiar", null).statusCode()).isEqualTo(401);
    }
    @Test void mapper422UsaSoloReglasPublicadasSinValorDelSecreto() throws Exception {
        var problema = PoliticaPasswordWebMapper.aProblema(new ValidacionPassword(List.of(
                new InfraccionPassword(ReglaPassword.LONGITUD_MINIMA, 10),
                new InfraccionPassword(ReglaPassword.YA_UTILIZADA, null))));
        assertThat(problema.getStatus()).isEqualTo(422);
        assertThat(problema.getProperties().get("code")).isEqualTo("POLITICA_INCUMPLIDA");
        assertThat(json.writeValueAsString(problema.getProperties().get("errores")))
                .isEqualTo("[{\"regla\":\"LONGITUD_MINIMA\",\"esperado\":10},{\"regla\":\"YA_UTILIZADA\"}]");
    }
}
