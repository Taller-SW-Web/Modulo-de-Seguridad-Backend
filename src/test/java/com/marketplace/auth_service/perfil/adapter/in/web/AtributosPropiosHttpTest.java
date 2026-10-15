package com.marketplace.auth_service.perfil.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.TokensDePrueba;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.perfil.application.dto.AtributosCliente;
import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.in.PerfilPort;
import com.marketplace.auth_service.perfil.domain.model.AtributoInvalidoException;
import com.marketplace.auth_service.perfil.domain.model.CuentaPerfilNoEncontradaException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
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
 * {@code PATCH /api/v1/usuarios/{id}/atributos} propio con el filtro de acceso real
 * (EP-08-T004, parte HTTP): presencia explícita, whitelist, 403 sin efecto parcial y
 * errores. El servicio es un doble; la validación de valores la prueban
 * {@code EditarPerfilPropioTest} y, con PostgreSQL, las pruebas de persistencia.
 * No cubre outbox ni auditoría (pendientes de EP-06).
 */
@SpringBootTest(classes = AtributosPropiosHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AtributosPropiosHttpTest {

    private static final String ISSUER = "https://seguridad.ejemplo.test/api/v1/auth";
    static final AtomicReference<BiFunction<ActorUsuario, CambioAtributos, PerfilProyectado>> COMPORTAMIENTO =
            new AtomicReference<>();
    static final AtomicReference<CambioAtributos> RECIBIDO = new AtomicReference<>();
    static final AtomicInteger LLAMADAS = new AtomicInteger();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, AtributosPropiosController.class,
            AtributosPropiosErroresAdvice.class})
    static class Aplicacion {
        @Bean
        PerfilPort perfilPort() {
            return new PerfilPort() {
                @Override
                public PerfilProyectado leerPropio(ActorUsuario actor) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public PerfilProyectado proyectarPara(ActorUsuario actor, UUID usuarioId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public PerfilProyectado actualizarPropio(ActorUsuario actor, CambioAtributos cambio) {
                    LLAMADAS.incrementAndGet();
                    RECIBIDO.set(cambio);
                    return COMPORTAMIENTO.get().apply(actor, cambio);
                }
            };
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
    private final UUID titular = UUID.randomUUID();

    @BeforeEach
    void reiniciar() {
        RECIBIDO.set(null);
        LLAMADAS.set(0);
        COMPORTAMIENTO.set((actor, cambio) -> perfil(actor.sub()));
    }

    private static PerfilProyectado perfil(UUID id) {
        return new PerfilProyectado(id, "Ana", "Quispe Rojas", "Ana Quispe Rojas", "ana@ejemplo.test",
                "+51987654321", "ACTIVO", true, false, false, List.of("CLIENTE"), new AtributosCliente(null), null,
                null);
    }

    private HttpResponse<String> patch(String id, String cuerpo, String bearer)
            throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/usuarios/" + id + "/atributos"))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(cuerpo));
        if (bearer != null) {
            peticion.header("Authorization", "Bearer " + bearer);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String token() {
        return firmaPort.firmar(new ClaimsAcceso(titular, "ana@ejemplo.test", List.of("CLIENTE"), List.of())).token();
    }

    private JsonNode problema(HttpResponse<String> respuesta) throws IOException {
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
        return json.readTree(respuesta.body());
    }

    @Test
    void soloLosCamposEnviadosLleganComoPresentes() throws Exception {
        HttpResponse<String> respuesta = patch(titular.toString(), "{\"nombres\":\"Ana\"}", token());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        CambioAtributos cambio = RECIBIDO.get();
        assertThat(cambio.nombres().presente()).isTrue();
        assertThat(cambio.nombres().valor()).isEqualTo("Ana");
        assertThat(cambio.apellidos().presente()).as("omitido conserva el dato").isFalse();
        assertThat(cambio.celular().presente()).as("omitido conserva el dato").isFalse();
    }

    @Test
    void unNullExplicitoLlegaComoPresenteConNull() throws Exception {
        patch(titular.toString(), "{\"celular\":null}", token());

        CambioAtributos cambio = RECIBIDO.get();
        assertThat(cambio.celular().presente()).isTrue();
        assertThat(cambio.celular().valor()).isNull();
        assertThat(cambio.nombres().presente()).isFalse();
    }

    @Test
    void laRespuestaEsElPerfilActualizadoConElEsquemaDeUsuario() throws Exception {
        HttpResponse<String> respuesta = patch(titular.toString(), "{\"nombres\":\"Ana\"}", token());

        JsonNode perfil = json.readTree(respuesta.body());
        assertThat(perfil.get("id").asText()).isEqualTo(titular.toString());
        assertThat(perfil.get("nombreCompleto").asText()).isEqualTo("Ana Quispe Rojas");
        assertThat(perfil.get("celular").asText()).isEqualTo("+51987654321");
        assertThat(perfil.get("mfaHabilitado").asBoolean()).isFalse();
        assertThat(perfil.has("documento")).as("nunca en claro").isFalse();
    }

    @Test
    void elTitularSaleDelTokenYOtroIdResponde403SinLlamarAlServicio() throws Exception {
        HttpResponse<String> respuesta = patch(UUID.randomUUID().toString(), "{\"nombres\":\"Ana\"}", token());

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(LLAMADAS.get()).isZero();
    }

    @Test
    void unAtributoPrivilegiadoRechazaTodoElCuerpoSinAplicarLaParteValida() throws Exception {
        HttpResponse<String> respuesta = patch(titular.toString(),
                "{\"nombres\":\"Ana\",\"numeroDocumento\":\"00000000\"}", token());

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(respuesta.body()).doesNotContain("00000000");
        assertThat(LLAMADAS.get()).isZero();
    }

    @Test
    void loQueNoPerteneceAlEsquemaNoLlegaAlServicio() throws Exception {
        HttpResponse<String> respuesta = patch(titular.toString(),
                "{\"nombres\":\"Ana\",\"correo\":\"otro@ejemplo.test\",\"roles\":[\"ADMIN_SISTEMA\"],"
                        + "\"estado\":\"BLOQUEADO\",\"mfaHabilitado\":true}", token());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        CambioAtributos cambio = RECIBIDO.get();
        assertThat(cambio.nombres().presente()).isTrue();
        assertThat(cambio.apellidos().presente()).isFalse();
        assertThat(cambio.celular().presente()).isFalse();
    }

    @Test
    void unTipoIncorrectoResponde400ValidacionConSuCampo() throws Exception {
        HttpResponse<String> respuesta = patch(titular.toString(), "{\"nombres\":123}", token());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        JsonNode problema = problema(respuesta);
        assertThat(problema.get("code").asText()).isEqualTo("VALIDACION");
        assertThat(problema.get("errores").get(0).get("campo").asText()).isEqualTo("nombres");
        assertThat(LLAMADAS.get()).isZero();
    }

    @Test
    void unaValidacionDelServicioResponde400SinRepetirElValor() throws Exception {
        COMPORTAMIENTO.set((actor, cambio) -> {
            throw new AtributoInvalidoException("celular", "formato +51 seguido de 9 dígitos");
        });

        HttpResponse<String> respuesta = patch(titular.toString(), "{\"celular\":\"+5199\"}", token());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        JsonNode problema = problema(respuesta);
        assertThat(problema.get("code").asText()).isEqualTo("VALIDACION");
        assertThat(problema.get("errores").get(0).get("campo").asText()).isEqualTo("celular");
        assertThat(problema.get("errores").get(0).get("mensaje").asText())
                .isEqualTo("formato +51 seguido de 9 dígitos");
        assertThat(respuesta.body()).doesNotContain("+5199");
    }

    @Test
    void unCuerpoQueNoEsJsonResponde400() throws Exception {
        HttpResponse<String> respuesta = patch(titular.toString(), "esto no es json", token());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        JsonNode problema = problema(respuesta);
        assertThat(problema.get("code").asText()).isEqualTo("VALIDACION");
        assertThat(problema.get("errores").get(0).get("campo").asText()).isEqualTo("cuerpo");
        assertThat(LLAMADAS.get()).isZero();
    }

    @Test
    void unIdQueNoEsUuidResponde400() throws Exception {
        HttpResponse<String> respuesta = patch("no-es-uuid", "{\"nombres\":\"Ana\"}", token());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        JsonNode problema = problema(respuesta);
        assertThat(problema.get("code").asText()).isEqualTo("VALIDACION");
        assertThat(problema.get("errores").get(0).get("campo").asText()).isEqualTo("id");
        assertThat(LLAMADAS.get()).isZero();
    }

    @Test
    void sinTokenResponde401() throws Exception {
        HttpResponse<String> respuesta = patch(titular.toString(), "{\"nombres\":\"Ana\"}", null);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("TOKEN_INVALIDO");
        assertThat(LLAMADAS.get()).isZero();
    }

    @Test
    void unTokenDeServicioResponde403TokenNoAplicable() throws Exception {
        String servicio = TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER).build());

        HttpResponse<String> respuesta = patch(titular.toString(), "{\"nombres\":\"Ana\"}", servicio);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("TOKEN_NO_APLICABLE");
        assertThat(LLAMADAS.get()).isZero();
    }

    @Test
    void unaCuentaQueYaNoExisteResponde401() throws Exception {
        COMPORTAMIENTO.set((actor, cambio) -> {
            throw new CuentaPerfilNoEncontradaException(actor.sub());
        });

        HttpResponse<String> respuesta = patch(titular.toString(), "{\"nombres\":\"Ana\"}", token());

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("TOKEN_INVALIDO");
    }

    @Test
    void unDocumentoIlegibleResponde503SinFiltrarElMotivo() throws Exception {
        COMPORTAMIENTO.set((actor, cambio) -> {
            throw new DocumentoNoLegibleException("sin clave");
        });

        HttpResponse<String> respuesta = patch(titular.toString(), "{\"nombres\":\"Ana\"}", token());

        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("NO_DISPONIBLE");
        assertThat(respuesta.body()).doesNotContain("sin clave");
    }
}
