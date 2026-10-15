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
import com.marketplace.auth_service.perfil.application.dto.AtributosVendedor;
import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.DocumentoProyectado;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.in.PerfilPort;
import com.marketplace.auth_service.perfil.domain.model.CuentaPerfilNoEncontradaException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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
 * {@code GET /api/v1/auth/me} con el filtro de acceso real (EP-08-T003): formato
 * {@code PerfilUsuario}, titular tomado del token y errores. El perfil es un doble;
 * con PostgreSQL y proveedores reales lo prueba {@code PerfilPropioPostgresTest}.
 */
@SpringBootTest(classes = PerfilHttpTest.Aplicacion.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PerfilHttpTest {

    private static final String ISSUER = "https://seguridad.ejemplo.test/api/v1/auth";
    static final AtomicReference<Function<ActorUsuario, PerfilProyectado>> COMPORTAMIENTO = new AtomicReference<>();
    static final AtomicReference<ActorUsuario> RECIBIDO = new AtomicReference<>();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, PerfilController.class, PerfilErroresAdvice.class})
    static class Aplicacion {
        @Bean
        PerfilPort perfilPort() {
            return new PerfilPort() {
                @Override
                public PerfilProyectado leerPropio(ActorUsuario actor) {
                    RECIBIDO.set(actor);
                    return COMPORTAMIENTO.get().apply(actor);
                }

                @Override
                public PerfilProyectado proyectarPara(ActorUsuario actor, UUID usuarioId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public PerfilProyectado actualizarPropio(ActorUsuario actor, CambioAtributos cambio) {
                    throw new UnsupportedOperationException();
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

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final UUID titular = UUID.randomUUID();

    @BeforeEach
    void reiniciar() {
        RECIBIDO.set(null);
        COMPORTAMIENTO.set(a -> perfil(a.sub(), List.of("CLIENTE"), new AtributosCliente(null), null, null, null));
    }

    private static PerfilProyectado perfil(UUID id, List<String> roles, AtributosCliente cliente,
                                           AtributosVendedor vendedor, DocumentoProyectado documento, String celular) {
        return new PerfilProyectado(id, "María", "Quispe Rojas", "María Quispe Rojas", "maria@ejemplo.test", celular,
                "ACTIVO", true, false, false, roles, cliente, vendedor, documento);
    }

    private HttpResponse<String> me(String bearer) throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto
                + "/api/v1/auth/me?usuarioId=" + UUID.randomUUID())).GET();
        if (bearer != null) {
            peticion.header("Authorization", "Bearer " + bearer);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String token() {
        return firmaPort.firmar(new ClaimsAcceso(titular, "maria@ejemplo.test", List.of("CLIENTE"), List.of())).token();
    }

    private JsonNode problema(HttpResponse<String> respuesta) throws IOException {
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
        return json.readTree(respuesta.body());
    }

    @Test
    void clienteSinFechaRegistradaLaRecibeComoNullYSinCelularNiDocumento() throws Exception {
        HttpResponse<String> respuesta = me(token());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode perfil = json.readTree(respuesta.body());
        assertThat(perfil.get("id").asText()).isEqualTo(titular.toString());
        assertThat(perfil.get("nombreCompleto").asText()).isEqualTo("María Quispe Rojas");
        assertThat(perfil.get("roles")).extracting(JsonNode::asText).containsExactly("CLIENTE");
        assertThat(perfil.get("mfaHabilitado").asBoolean()).isFalse();
        assertThat(perfil.has("fechaNacimiento")).isTrue();
        assertThat(perfil.get("fechaNacimiento").isNull()).isTrue();
        assertThat(perfil.has("celular")).isFalse();
        assertThat(perfil.has("codigoVendedor")).as("no es VENDEDOR").isFalse();
        assertThat(perfil.has("documentoEnmascarado")).isFalse();
    }

    @Test
    void elTitularSaleDelTokenYNoDeLaPeticion() throws Exception {
        me(token());

        assertThat(RECIBIDO.get().sub()).isEqualTo(titular);
    }

    @Test
    void vendedorConDocumentoEnmascaradoYCelular() throws Exception {
        COMPORTAMIENTO.set(a -> perfil(a.sub(), List.of("CLIENTE", "VENDEDOR"),
                new AtributosCliente(LocalDate.parse("1990-05-04")),
                new AtributosVendedor("VEND-1042", "Tienda Centro", null),
                new DocumentoProyectado("DNI", "*****234"), "+51987654321"));

        JsonNode perfil = json.readTree(me(token()).body());

        assertThat(perfil.get("fechaNacimiento").asText()).isEqualTo("1990-05-04");
        assertThat(perfil.get("codigoVendedor").asText()).isEqualTo("VEND-1042");
        assertThat(perfil.get("tienda").asText()).isEqualTo("Tienda Centro");
        assertThat(perfil.get("fechaIngreso").isNull()).isTrue();
        assertThat(perfil.get("tipoDocumento").asText()).isEqualTo("DNI");
        assertThat(perfil.get("documentoEnmascarado").asText()).isEqualTo("*****234");
        assertThat(perfil.get("celular").asText()).isEqualTo("+51987654321");
        assertThat(perfil.has("documento")).as("nunca en claro").isFalse();
    }

    @Test
    void sinTokenResponde401() throws Exception {
        HttpResponse<String> respuesta = me(null);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("TOKEN_INVALIDO");
        assertThat(RECIBIDO.get()).isNull();
    }

    @Test
    void unTokenDeServicioResponde403TokenNoAplicable() throws Exception {
        String servicio = TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER).build());

        HttpResponse<String> respuesta = me(servicio);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("TOKEN_NO_APLICABLE");
        assertThat(RECIBIDO.get()).isNull();
    }

    @Test
    void unaCuentaQueYaNoExisteResponde401() throws Exception {
        COMPORTAMIENTO.set(a -> {
            throw new CuentaPerfilNoEncontradaException(a.sub());
        });

        HttpResponse<String> respuesta = me(token());

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("TOKEN_INVALIDO");
    }

    @Test
    void unDocumentoIlegibleNoSeOcultaYResponde503() throws Exception {
        COMPORTAMIENTO.set(a -> {
            throw new DocumentoNoLegibleException("sin clave");
        });

        HttpResponse<String> respuesta = me(token());

        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("NO_DISPONIBLE");
        assertThat(respuesta.body()).doesNotContain("sin clave");
    }
}
