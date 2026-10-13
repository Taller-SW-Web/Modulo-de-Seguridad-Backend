package com.marketplace.auth_service.autorizacion.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.autorizacion.application.dto.CambioRolCommand;
import com.marketplace.auth_service.autorizacion.application.dto.CuentaRoles;
import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiados;
import com.marketplace.auth_service.autorizacion.application.port.in.GestionRolesPort;
import com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException;
import com.marketplace.auth_service.autorizacion.domain.exception.GestionRolesNoDisponibleException;
import com.marketplace.auth_service.autorizacion.domain.exception.UsuarioNoEncontradoException;
import com.marketplace.auth_service.autorizacion.domain.model.Permiso;
import com.marketplace.auth_service.autorizacion.domain.model.Rol;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POST {@code /api/v1/usuarios/{id}/roles} sobre un servidor real con el filtro
 * de acceso real (EP-05-T003): formato de la respuesta y de cada error del
 * contrato. El caso de uso es un doble programable; su lógica se prueba en
 * {@code GestionRolesServiceTest} y sobre PostgreSQL en {@code GestionRolesPostgresTest}.
 */
@SpringBootTest(classes = RolesHttpTest.Aplicacion.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RolesHttpTest {

    private static final String ISSUER = "https://seguridad.ejemplo.test/api/v1/auth";
    /** Lo que hará el caso de uso en la próxima petición; recibe el comando que armó el controlador. */
    static final AtomicReference<Function<CambioRolCommand, RolesCambiados>> COMPORTAMIENTO = new AtomicReference<>();
    static final AtomicReference<CambioRolCommand> RECIBIDO = new AtomicReference<>();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, RolesController.class, RolesErroresAdvice.class,
            AutorizacionErroresAdvice.class})
    static class Aplicacion {

        @Bean
        GestionRolesPort gestionRolesPort() {
            return comando -> {
                RECIBIDO.set(comando);
                return COMPORTAMIENTO.get().apply(comando);
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
    private final UUID admin = UUID.randomUUID();
    private final UUID objetivo = UUID.randomUUID();

    @BeforeEach
    void reiniciar() {
        RECIBIDO.set(null);
        COMPORTAMIENTO.set(c -> new RolesCambiados(
                new CuentaRoles(c.usuarioObjetivo(), "Valeria Ríos", "valeria@ejemplo.test", "ACTIVO"),
                List.of(Rol.VENDEDOR, Rol.GESTOR_DESPACHO), true));
    }

    private String token(UUID sub) {
        return firmaPort.firmar(new ClaimsAcceso(sub, "a@ejemplo.test", List.of("ADMIN_SISTEMA"),
                List.of("rol.asignar"))).token();
    }

    private HttpResponse<String> post(String ruta, String cuerpo, String bearer) throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .header("User-Agent", "prueba-roles")
                .POST(cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (bearer != null) {
            peticion.header("Authorization", "Bearer " + bearer);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> asignar(String cuerpo) throws IOException, InterruptedException {
        return post("/api/v1/usuarios/" + objetivo + "/roles", cuerpo, token(admin));
    }

    private JsonNode problema(HttpResponse<String> respuesta) throws IOException {
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
        return json.readTree(respuesta.body());
    }

    // ── 200 ───────────────────────────────────────────────────

    @Test
    void asignaYRespondeElUsuarioConLaListaCompletaDeRoles() throws Exception {
        HttpResponse<String> respuesta = asignar("{\"rol\":\"GESTOR_DESPACHO\"}");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode usuario = json.readTree(respuesta.body());
        assertThat(usuario.get("id").asText()).isEqualTo(objetivo.toString());
        assertThat(usuario.get("nombreCompleto").asText()).isEqualTo("Valeria Ríos");
        assertThat(usuario.get("correo").asText()).isEqualTo("valeria@ejemplo.test");
        assertThat(usuario.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(usuario.get("roles")).extracting(JsonNode::asText).containsExactly("VENDEDOR", "GESTOR_DESPACHO");
    }

    @Test
    void elActorSaleDelTokenYNoDelCuerpo() throws Exception {
        asignar("{\"rol\":\"GESTOR_DESPACHO\",\"actor\":\"" + UUID.randomUUID() + "\",\"sub\":\"otro\"}");

        CambioRolCommand comando = RECIBIDO.get();
        assertThat(comando.actor().sub()).isEqualTo(admin);
        assertThat(comando.usuarioObjetivo()).isEqualTo(objetivo);
        assertThat(comando.rol()).isEqualTo("GESTOR_DESPACHO");
        assertThat(comando.contexto().ip()).isNotBlank();
        assertThat(comando.contexto().agente()).isEqualTo("prueba-roles");
    }

    // ── 400 VALIDACION ────────────────────────────────────────

    @Test
    void unRolFueraDelCatalogoResponde400ConElCampo() throws Exception {
        COMPORTAMIENTO.set(c -> {
            Rol.desdeCodigo(c.rol());
            throw new AssertionError("no debía llegar aquí");
        });

        HttpResponse<String> respuesta = asignar("{\"rol\":\"SUPER_ADMIN\"}");

        assertThat(respuesta.statusCode()).isEqualTo(400);
        JsonNode p = problema(respuesta);
        assertThat(p.get("code").asText()).isEqualTo("VALIDACION");
        assertThat(p.get("title").asText()).isEqualTo("Datos de entrada inválidos");
        assertThat(p.get("type").asText()).isEqualTo("https://g7.unmsm.pe/errores/validacion");
        assertThat(p.get("errores").get(0).get("campo").asText()).isEqualTo("rol");
        assertThat(p.get("errores").get(0).get("mensaje").asText()).isEqualTo("No pertenece al catálogo cerrado de roles.");
    }

    @Test
    void sinRolResponde400EsObligatorio() throws Exception {
        COMPORTAMIENTO.set(c -> {
            Rol.desdeCodigo(c.rol());
            throw new AssertionError("no debía llegar aquí");
        });

        for (String cuerpo : new String[]{"{}", "{\"rol\":null}"}) {
            HttpResponse<String> respuesta = asignar(cuerpo);
            assertThat(respuesta.statusCode()).as(cuerpo).isEqualTo(400);
            assertThat(problema(respuesta).get("errores").get(0).get("mensaje").asText()).isEqualTo("Es obligatorio.");
        }
    }

    @Test
    void unCuerpoQueNoEsJsonResponde400() throws Exception {
        HttpResponse<String> respuesta = asignar("rol=ADMIN_SISTEMA");

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("VALIDACION");
        assertThat(RECIBIDO.get()).isNull();
    }

    @Test
    void unIdQueNoEsUuidResponde400() throws Exception {
        HttpResponse<String> respuesta = post("/api/v1/usuarios/no-es-uuid/roles", "{\"rol\":\"VENDEDOR\"}",
                token(admin));

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(problema(respuesta).get("errores").get(0).get("campo").asText()).isEqualTo("id");
    }

    // ── 401 / 403 / 404 / 503 ─────────────────────────────────

    @Test
    void sinTokenResponde401SinLlegarAlCasoDeUso() throws Exception {
        HttpResponse<String> respuesta = post("/api/v1/usuarios/" + objetivo + "/roles", "{\"rol\":\"VENDEDOR\"}", null);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("TOKEN_INVALIDO");
        assertThat(RECIBIDO.get()).isNull();
    }

    @Test
    void sinPermisoResponde403SinDecirCualFalta() throws Exception {
        COMPORTAMIENTO.set(c -> {
            throw new AccesoDenegadoException(c.actor().sub(), Permiso.ROL_ASIGNAR);
        });

        HttpResponse<String> respuesta = asignar("{\"rol\":\"ADMIN_SISTEMA\"}");

        assertThat(respuesta.statusCode()).isEqualTo(403);
        JsonNode p = problema(respuesta);
        assertThat(p.get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(p.get("title").asText()).isEqualTo("Permisos insuficientes");
        assertThat(respuesta.body()).doesNotContain("rol.asignar");
    }

    @Test
    void unaCuentaInexistenteResponde404() throws Exception {
        COMPORTAMIENTO.set(c -> {
            throw new UsuarioNoEncontradoException(c.usuarioObjetivo());
        });

        HttpResponse<String> respuesta = asignar("{\"rol\":\"VENDEDOR\"}");

        assertThat(respuesta.statusCode()).isEqualTo(404);
        JsonNode p = problema(respuesta);
        assertThat(p.get("code").asText()).isEqualTo("NO_ENCONTRADO");
        assertThat(p.get("title").asText()).isEqualTo("Usuario no encontrado");
    }

    @Test
    void sinProveedoresOConUnFalloTecnicoResponde503() throws Exception {
        COMPORTAMIENTO.set(c -> {
            throw new GestionRolesNoDisponibleException("faltan: [CuentasRolesPort (cuentas, Eva)]");
        });

        HttpResponse<String> respuesta = asignar("{\"rol\":\"VENDEDOR\"}");

        assertThat(respuesta.statusCode()).isEqualTo(503);
        JsonNode p = problema(respuesta);
        assertThat(p.get("code").asText()).isEqualTo("NO_DISPONIBLE");
        assertThat(respuesta.body()).as("no revela qué falta en el servidor").doesNotContain("CuentasRolesPort");
    }
}
