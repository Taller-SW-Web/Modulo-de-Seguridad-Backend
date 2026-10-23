package com.marketplace.auth_service.usuario.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.autorizacion.adapter.in.web.AutorizacionErroresAdvice;
import com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.usuario.application.dto.BloqueoVista;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.RolesDeCuenta;
import com.marketplace.auth_service.usuario.application.port.in.ConsultaUsuariosPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.port.out.AutorizacionUsuariosPort;
import com.marketplace.auth_service.usuario.application.port.out.BloqueosConsultaPort;
import com.marketplace.auth_service.usuario.application.service.ConsultaUsuariosService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
 * {@code GET /usuarios} y {@code GET /usuarios/{id}} sobre un servidor real con el
 * filtro de acceso real y el caso de uso real (EP-01-T003/T004). Cuentas y
 * autorización son dobles en memoria; con PostgreSQL y proveedores reales lo
 * prueba {@code ConsultaUsuariosPostgresTest}.
 */
@SpringBootTest(classes = UsuariosHttpTest.Aplicacion.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsuariosHttpTest {

    private static final String ISSUER = "https://seguridad.ejemplo.test/api/v1/auth";
    static final UUID ADMIN = UUID.randomUUID();
    static final UUID ANA = UUID.randomUUID();
    static final AtomicReference<FiltroCuenta> FILTRO = new AtomicReference<>();
    static final AtomicReference<Paginacion> PAGINACION = new AtomicReference<>();

    static final Map<UUID, CuentaSnapshot> CUENTAS = new HashMap<>();
    static final Instant HASTA = Instant.parse("2026-10-17T15:30:00Z");
    static final AtomicReference<Set<UUID>> CONSULTADOS = new AtomicReference<>();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, UsuariosController.class, UsuariosErroresAdvice.class,
            AutorizacionErroresAdvice.class})
    static class Aplicacion {
        @Bean
        ConsultaUsuariosPort consultaUsuariosPort() {
            return new ConsultaUsuariosService(cuentas(), autorizacion(), bloqueos());
        }

        /** Solo ADMIN tiene un bloqueo vigente; registra por qué cuentas se preguntó. */
        private static BloqueosConsultaPort bloqueos() {
            return ids -> {
                CONSULTADOS.set(Set.copyOf(ids));
                return ids.contains(ADMIN) ? Map.of(ADMIN, new BloqueoVista("AUTOMATICO", HASTA, null)) : Map.of();
            };
        }

        private static AutorizacionUsuariosPort autorizacion() {
            return new AutorizacionUsuariosPort() {
                @Override
                public void exigirVerUsuarios(ActorUsuario actor) {
                    if (!ADMIN.equals(actor.sub())) {
                        throw new AccesoDenegadoException(actor.sub(), Permiso.USUARIO_VER);
                    }
                }

                @Override
                public Map<UUID, RolesDeCuenta> rolesDe(Set<UUID> ids) {
                    Map<UUID, RolesDeCuenta> roles = new HashMap<>();
                    ids.forEach(id -> roles.put(id, id.equals(ADMIN)
                            ? new RolesDeCuenta(List.of("ADMIN_SISTEMA"), true)
                            : new RolesDeCuenta(List.of("CLIENTE"), false)));
                    return roles;
                }
            };
        }

        private static CuentasPort cuentas() {
            return new CuentasPort() {
                @Override
                public Optional<CuentaSnapshot> buscarPorCorreoNormalizado(String correo) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Optional<CuentaSnapshot> leer(UUID id) {
                    return Optional.ofNullable(CUENTAS.get(id));
                }

                @Override
                public CuentaSnapshot bloquear(UUID id) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public long contarActivos(Set<UUID> ids) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public PaginaResultado<CuentaSnapshot> consultar(FiltroCuenta filtro, Paginacion paginacion) {
                    FILTRO.set(filtro);
                    PAGINACION.set(paginacion);
                    return new PaginaResultado<>(List.of(CUENTAS.get(ANA), CUENTAS.get(ADMIN)),
                            paginacion.pagina(), paginacion.tamano(), 42);
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

    @BeforeEach
    void cuentas() {
        FILTRO.set(null);
        PAGINACION.set(null);
        CONSULTADOS.set(null);
        CUENTAS.put(ANA, new CuentaSnapshot(ANA, "Ana", "Pérez", "ana@clientes.test", null, "ACTIVO", "ACTIVO",
                true, false, false, 3, 2));
        CUENTAS.put(ADMIN, new CuentaSnapshot(ADMIN, "Adm", "Sistema", "admin@g7.test", "+51987654321",
                "ACTIVO", "BLOQUEADO", true, true, false, 0, 0));
    }

    private String token(UUID sub) {
        return firmaPort.firmar(new ClaimsAcceso(sub, "x@ejemplo.test", List.of("ADMIN_SISTEMA"),
                List.of("usuario.ver"))).token();
    }

    private HttpResponse<String> get(String ruta, UUID sub) throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET();
        if (sub != null) {
            peticion.header("Authorization", "Bearer " + token(sub));
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode problema(HttpResponse<String> respuesta) throws IOException {
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
        return json.readTree(respuesta.body());
    }

    // ── 200 ───────────────────────────────────────────────────

    @Test
    void listaConElFormatoPaginaUsuariosYSinCamposSensibles() throws Exception {
        HttpResponse<String> respuesta = get("/api/v1/usuarios?estado=ACTIVO&rol=CLIENTE&correo=Ana&pagina=2&tamano=5",
                ADMIN);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode pagina = json.readTree(respuesta.body());
        assertThat(pagina.get("pagina").asInt()).isEqualTo(2);
        assertThat(pagina.get("tamano").asInt()).isEqualTo(5);
        assertThat(pagina.get("total").asLong()).isEqualTo(42);
        JsonNode ana = pagina.get("usuarios").get(0);
        assertThat(ana.get("id").asText()).isEqualTo(ANA.toString());
        assertThat(ana.get("nombreCompleto").asText()).isEqualTo("Ana Pérez");
        assertThat(ana.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(ana.get("roles")).extracting(JsonNode::asText).containsExactly("CLIENTE");
        assertThat(ana.has("celular")).as("celular ausente se omite").isFalse();
        // Estado efectivo, no el de la tabla; MFA efectivo por rol de gestión.
        JsonNode admin = pagina.get("usuarios").get(1);
        assertThat(admin.get("estado").asText()).isEqualTo("BLOQUEADO");
        assertThat(admin.get("mfaHabilitado").asBoolean()).isTrue();
        // RF-15.6: bloqueo solo en la cuenta BLOQUEADO, y protección solo se consulta por ella.
        assertThat(admin.get("bloqueo").get("tipo").asText()).isEqualTo("AUTOMATICO");
        assertThat(admin.get("bloqueo").get("hasta").asText()).isEqualTo("2026-10-17T15:30:00Z");
        assertThat(admin.get("bloqueo").has("motivo")).isFalse();
        assertThat(ana.has("bloqueo")).as("sin bloqueo se omite").isFalse();
        assertThat(CONSULTADOS.get()).containsExactly(ADMIN);
        assertThat(respuesta.body()).doesNotContain("intentos", "bloqueosSeguidos", "estadoBase", "password",
                "hash", "documento");

        assertThat(FILTRO.get()).isEqualTo(new FiltroCuenta("ACTIVO", "CLIENTE", "Ana"));
        assertThat(PAGINACION.get()).isEqualTo(new Paginacion(2, 5));
    }

    @Test
    void sinParametrosUsaPagina1Tamano20() throws Exception {
        assertThat(get("/api/v1/usuarios", ADMIN).statusCode()).isEqualTo(200);
        assertThat(PAGINACION.get()).isEqualTo(new Paginacion(1, 20));
        assertThat(FILTRO.get()).isEqualTo(new FiltroCuenta(null, null, null));
    }

    @Test
    void detalleDeUnaCuenta() throws Exception {
        HttpResponse<String> respuesta = get("/api/v1/usuarios/" + ADMIN, ADMIN);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode usuario = json.readTree(respuesta.body());
        assertThat(usuario.get("correo").asText()).isEqualTo("admin@g7.test");
        assertThat(usuario.get("celular").asText()).isEqualTo("+51987654321");
        assertThat(usuario.get("celularVerificado").asBoolean()).isTrue();
        assertThat(usuario.get("roles")).extracting(JsonNode::asText).containsExactly("ADMIN_SISTEMA");
        assertThat(usuario.get("bloqueo").get("tipo").asText()).isEqualTo("AUTOMATICO");
    }

    @Test
    void detalleDeUnaCuentaActivaNoConsultaBloqueos() throws Exception {
        HttpResponse<String> respuesta = get("/api/v1/usuarios/" + ANA, ADMIN);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(json.readTree(respuesta.body()).has("bloqueo")).isFalse();
        assertThat(CONSULTADOS.get()).as("no se pregunta a protección").isNull();
    }

    // ── 401 / 403 ─────────────────────────────────────────────

    @Test
    void sinTokenResponde401() throws Exception {
        for (String ruta : List.of("/api/v1/usuarios", "/api/v1/usuarios/" + ANA)) {
            HttpResponse<String> respuesta = get(ruta, null);
            assertThat(respuesta.statusCode()).as(ruta).isEqualTo(401);
            assertThat(problema(respuesta).get("code").asText()).isEqualTo("TOKEN_INVALIDO");
        }
    }

    @Test
    void sinPermisoResponde403AntesDeValidarYSinRevelarSiExiste() throws Exception {
        for (String ruta : List.of("/api/v1/usuarios", "/api/v1/usuarios?estado=X&pagina=0",
                "/api/v1/usuarios/" + ANA, "/api/v1/usuarios/" + UUID.randomUUID(), "/api/v1/usuarios/no-es-uuid")) {
            HttpResponse<String> respuesta = get(ruta, ANA);
            assertThat(respuesta.statusCode()).as(ruta).isEqualTo(403);
            assertThat(problema(respuesta).get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        }
        assertThat(FILTRO.get()).as("no se consultó").isNull();
        assertThat(CONSULTADOS.get()).as("sin permiso no se lee el bloqueo ni su motivo").isNull();
    }

    // ── 400 / 404 ─────────────────────────────────────────────

    @Test
    void parametrosInvalidosResponden400ConElCampo() throws Exception {
        Map<String, String> casos = Map.of(
                "estado=ELIMINADO", "estado",
                "rol=SUPER_ADMIN", "rol",
                "pagina=0", "pagina",
                "pagina=uno", "pagina",
                "tamano=101", "tamano",
                "tamano=0", "tamano",
                "correo=" + "a".repeat(255), "correo");
        for (var caso : casos.entrySet()) {
            HttpResponse<String> respuesta = get("/api/v1/usuarios?" + caso.getKey(), ADMIN);
            assertThat(respuesta.statusCode()).as(caso.getKey()).isEqualTo(400);
            JsonNode p = problema(respuesta);
            assertThat(p.get("code").asText()).isEqualTo("VALIDACION");
            assertThat(p.get("errores").get(0).get("campo").asText()).as(caso.getKey()).isEqualTo(caso.getValue());
        }
        HttpResponse<String> idInvalido = get("/api/v1/usuarios/no-es-uuid", ADMIN);
        assertThat(idInvalido.statusCode()).isEqualTo(400);
        assertThat(problema(idInvalido).get("errores").get(0).get("campo").asText()).isEqualTo("id");
    }

    @Test
    void unaCuentaInexistenteResponde404() throws Exception {
        HttpResponse<String> respuesta = get("/api/v1/usuarios/" + UUID.randomUUID(), ADMIN);

        assertThat(respuesta.statusCode()).isEqualTo(404);
        JsonNode p = problema(respuesta);
        assertThat(p.get("code").asText()).isEqualTo("NO_ENCONTRADO");
        assertThat(p.get("type").asText()).isEqualTo("https://g7.unmsm.pe/errores/no-encontrado");
    }
}
