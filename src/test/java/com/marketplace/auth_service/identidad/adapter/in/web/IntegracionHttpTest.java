package com.marketplace.auth_service.identidad.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.autorizacion.adapter.in.web.CatalogoController;
import com.marketplace.auth_service.autorizacion.application.dto.PermisoCatalogado;
import com.marketplace.auth_service.autorizacion.application.dto.RolCatalogado;
import com.marketplace.auth_service.autorizacion.application.port.in.CatalogoPort;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ClaimsServicio;
import com.marketplace.auth_service.identidad.application.dto.DenegacionAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.port.in.IntrospeccionPort;
import com.marketplace.auth_service.identidad.application.port.in.TokenServicioPort;
import com.marketplace.auth_service.identidad.application.port.out.AuditoriaAccesoPort;
import com.marketplace.auth_service.identidad.application.port.out.EstadoIdentidadPort;
import com.marketplace.auth_service.identidad.application.service.EmitirTokenServicioService;
import com.marketplace.auth_service.identidad.application.service.IntrospeccionService;
import com.marketplace.auth_service.identidad.domain.model.ClienteServicio;
import com.marketplace.auth_service.identidad.domain.model.ScopeConcedido;
import com.marketplace.auth_service.identidad.domain.model.SecretoCliente;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Integración de módulos sobre un servidor HTTP real (SPEC-17 ESC-17.3/17.4/
 * 17.5/17.7/17.8, SPEC-18 ESC-18.7): token de servicio, introspección, catálogo
 * y la frontera entre tokens de persona y de servicio.
 */
@SpringBootTest(classes = IntegracionHttpTest.Aplicacion.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class IntegracionHttpTest {

    private static final int PUERTO = puertoLibre();
    private static final String BASE = "http://localhost:" + PUERTO;
    private static final String ISSUER = BASE + "/api/v1/auth";
    private static final String SECRETO = "secreto-de-prueba-solo-para-este-test-0123456789";
    private static final Set<UUID> ACCESOS_VIGENTES = ConcurrentHashMap.newKeySet();
    private static final List<DenegacionAcceso> DENEGACIONES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean FALLA_AUDITORIA = new AtomicBoolean();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, IntegracionController.class, CatalogoController.class,
            ControladorDePersona.class})
    static class Aplicacion {

        @Bean
        TokenServicioPort tokenServicioPort(FirmaPort firma) {
            Map<String, ClienteServicio> clientes = Map.of(
                    "modulo-productos", new ClienteServicio("modulo-productos", SecretoCliente.hash(SECRETO), true,
                            List.of(new ScopeConcedido("tokens:introspeccion", "api-seguridad"),
                                    new ScopeConcedido("roles:leer", "api-seguridad"))),
                    "modulo-ventas", new ClienteServicio("modulo-ventas", SecretoCliente.hash(SECRETO), true,
                            List.of(new ScopeConcedido("usuarios:leer", "api-seguridad"),
                                    new ScopeConcedido("despachos:crear", "api-despacho"))));
            return new EmitirTokenServicioService(id -> Optional.ofNullable(clientes.get(id)), firma);
        }

        @Bean
        IntrospeccionPort introspeccionPort(FirmaPort firma) {
            return new IntrospeccionService(firma, new EstadoIdentidadPort() {
                @Override
                public boolean accesoVigente(UUID sub, UUID jti) {
                    return ACCESOS_VIGENTES.contains(jti);
                }

                @Override
                public Optional<Cuenta> cuenta(UUID sub) {
                    return Optional.of(new Cuenta("ACTIVO", "carlos@ejemplo.com"));
                }

                @Override
                public Permisos permisos(UUID sub) {
                    return new Permisos(List.of("VENDEDOR"), List.of("cliente.buscar", "cliente.registrar"));
                }
            });
        }

        /** Registra cada 403 de scope (ESC-17.8); puede simular que la auditoría falla. */
        @Bean
        AuditoriaAccesoPort auditoriaAccesoPort() {
            return denegacion -> {
                if (FALLA_AUDITORIA.get()) {
                    throw new IllegalStateException("auditoría caída");
                }
                DENEGACIONES.add(denegacion);
            };
        }

        @Bean
        CatalogoPort catalogoPort() {
            return new CatalogoPort() {
                @Override
                public List<RolCatalogado> roles() {
                    return List.of(new RolCatalogado("CLIENTE", "Cliente", "Persona que compra", List.of()),
                            new RolCatalogado("VENDEDOR", "Vendedor", "Vendedor de tienda",
                                    List.of("cliente.buscar", "cliente.registrar")));
                }

                @Override
                public List<PermisoCatalogado> permisos(String modulo) {
                    return List.of(new PermisoCatalogado("usuario.ver", "seguridad", "Listar y consultar"));
                }
            };
        }
    }

    /** Cualquier operación de persona: un token de servicio no entra (ESC-17.4). */
    @RestController
    static class ControladorDePersona {
        @GetMapping("/api/v1/prueba/persona")
        Map<String, String> persona(@AuthenticationPrincipal ActorUsuario actor) {
            return Map.of("sub", actor.sub().toString());
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("server.port", () -> PUERTO);
        registro.add("identidad.issuer", () -> ISSUER);
        registro.add("identidad.clave-privada", () -> ClavesDePrueba.pemPkcs8(ClavesDePrueba.RSA_3072));
    }

    @Autowired
    private FirmaPort firmaPort;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    // ── POST /auth/token ─────────────────────────────────────

    @Test
    void unModuloObtieneSuTokenDeServicio() throws Exception {
        HttpResponse<String> respuesta = token("modulo-productos", SECRETO, "roles:leer usuarios:leer");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().firstValue("Cache-Control")).hasValueSatisfying(
                v -> assertThat(v).contains("no-store"));
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("accessToken", "tokenType", "expiresIn", "scopes");
        assertThat(cuerpo.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(cuerpo.get("expiresIn").asInt()).isEqualTo(3600);
        assertThat(cuerpo.get("scopes")).extracting(JsonNode::asText)
                .containsExactly("roles:leer", "tokens:introspeccion");
        assertThat(firmaPort.validarServicio(cuerpo.get("accessToken").asText()).clientId())
                .isEqualTo("modulo-productos");
    }

    @Test
    void credencialesDeClienteInvalidasResponden401ClienteInvalido() throws Exception {
        for (HttpResponse<String> respuesta : List.of(token("modulo-productos", "otro", null),
                token("modulo-fantasma", SECRETO, null))) {
            assertThat(respuesta.statusCode()).isEqualTo(401);
            JsonNode problema = json.readTree(respuesta.body());
            assertThat(problema.get("code").asText()).isEqualTo("CLIENTE_INVALIDO");
            assertThat(problema.get("type").asText()).isEqualTo("https://g7.unmsm.pe/errores/cliente-invalido");
            assertThat(problema.get("title").asText()).isEqualTo("Credenciales de cliente inválidas");
        }
    }

    @Test
    void otroGrantTypeEsValidacion() throws Exception {
        HttpResponse<String> respuesta = post("/api/v1/auth/token", null, "application/x-www-form-urlencoded",
                "grant_type=password&client_id=modulo-productos&client_secret=" + enc(SECRETO));

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(json.readTree(respuesta.body()).get("code").asText()).isEqualTo("VALIDACION");
    }

    // ── POST /auth/introspeccion ─────────────────────────────

    @Test
    void conTokensIntrospeccionDevuelveElEstadoActual() throws Exception {
        AccesoFirmado acceso = firmaPort.firmar(new ClaimsAcceso(UUID.randomUUID(), "carlos@ejemplo.com",
                List.of("VENDEDOR"), List.of()));
        ACCESOS_VIGENTES.add(acceso.jti());

        HttpResponse<String> respuesta = introspeccionar(tokenDe("modulo-productos"), acceso.token());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("activo").asBoolean()).isTrue();
        assertThat(cuerpo.get("tipo").asText()).isEqualTo("acceso");
        assertThat(cuerpo.get("roles")).extracting(JsonNode::asText).containsExactly("VENDEDOR");
        assertThat(cuerpo.get("exp").asLong()).isEqualTo(acceso.expiraEn().getEpochSecond());
        assertThat(cuerpo.has("motivo")).isFalse();
    }

    @Test
    void unAccesoCerradoSaleInactivoCon200() throws Exception {
        AccesoFirmado cerrado = firmaPort.firmar(new ClaimsAcceso(UUID.randomUUID(), "x@ejemplo.com",
                List.of("VENDEDOR"), List.of()));

        HttpResponse<String> respuesta = introspeccionar(tokenDe("modulo-productos"), cerrado.token());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.fieldNames()).toIterable().containsExactlyInAnyOrder("activo", "motivo");
        assertThat(cuerpo.get("motivo").asText()).isEqualTo("TOKEN_REVOCADO");
    }

    @Test
    void sinTokenDeServicio401YSinElScopeOConTokenDePersona403SinRevelarCual() throws Exception {
        String acceso = firmaPort.firmar(new ClaimsAcceso(UUID.randomUUID(), "x@ejemplo.com", List.of(), List.of()))
                .token();

        HttpResponse<String> sinToken = introspeccionar(null, acceso);
        HttpResponse<String> sinScope = introspeccionar(tokenDe("modulo-ventas"), acceso);
        HttpResponse<String> dePersona = introspeccionar(acceso, acceso);

        assertThat(sinToken.statusCode()).isEqualTo(401);
        assertThat(json.readTree(sinToken.body()).get("code").asText()).isEqualTo("TOKEN_INVALIDO");
        for (HttpResponse<String> respuesta : List.of(sinScope, dePersona)) {
            assertThat(respuesta.statusCode()).isEqualTo(403);
            JsonNode problema = json.readTree(respuesta.body());
            assertThat(problema.get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
            assertThat(respuesta.body()).doesNotContain("tokens:introspeccion");
        }
    }

    @Test
    void cadaScopeInsuficienteSeAuditaComoAccesoDenegadoSinElScopeQueFaltaba() throws Exception {
        UUID persona = UUID.randomUUID();
        String acceso = firmaPort.firmar(new ClaimsAcceso(persona, "x@ejemplo.com", List.of(), List.of())).token();
        DENEGACIONES.clear();

        introspeccionar(tokenDe("modulo-ventas"), acceso);
        get("/api/v1/roles", acceso);

        assertThat(DENEGACIONES).extracting(DenegacionAcceso::actorTipo, DenegacionAcceso::actorId,
                DenegacionAcceso::metodo, DenegacionAcceso::ruta).containsExactly(
                tuple("MODULO", "modulo-ventas", "POST", "/api/v1/auth/introspeccion"),
                tuple("USUARIO", persona.toString(), "GET", "/api/v1/roles"));
        assertThat(DENEGACIONES).allSatisfy(d -> {
            assertThat(d.motivo()).isEqualTo("SCOPE_INSUFICIENTE");
            assertThat(d.jti()).isNotNull();
        });
        assertThat(DENEGACIONES.toString()).doesNotContain("tokens:introspeccion", "roles:leer", acceso);
    }

    @Test
    void siLaAuditoriaFallaElScopeInsuficienteSigueSiendo403() throws Exception {
        FALLA_AUDITORIA.set(true);
        try {
            HttpResponse<String> respuesta = introspeccionar(tokenDe("modulo-ventas"), "cualquiera");

            assertThat(respuesta.statusCode()).isEqualTo(403);
            assertThat(json.readTree(respuesta.body()).get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        } finally {
            FALLA_AUDITORIA.set(false);
        }
    }

    @Test
    void lasRespuestasPermitidasNoSeAuditan() throws Exception {
        DENEGACIONES.clear();

        get("/api/v1/roles", tokenDe("modulo-productos"));
        introspeccionar(null, "cualquiera");

        assertThat(DENEGACIONES).isEmpty();
    }

    @Test
    void unTokenDeOtraApiNoAutenticaAqui() throws Exception {
        String soloDespacho = firmaPort.firmarServicio(new ClaimsServicio("modulo-ventas", List.of("api-despacho"),
                List.of("despachos:crear"))).token();

        HttpResponse<String> respuesta = get("/api/v1/roles", soloDespacho);

        assertThat(respuesta.statusCode()).isEqualTo(401);
    }

    // ── Persona frente a servicio (ESC-17.4) ─────────────────

    @Test
    void unTokenDeServicioNoActuaEnNombreDeUnaPersona() throws Exception {
        String servicio = tokenDe("modulo-productos");
        UUID jti = firmaPort.validarServicio(servicio).jti();
        DENEGACIONES.clear();

        HttpResponse<String> respuesta = get("/api/v1/prueba/persona", servicio);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(json.readTree(respuesta.body()).get("code").asText()).isEqualTo("TOKEN_NO_APLICABLE");
        // ESC-17.4: el intento queda registrado, con el módulo y el jti, nunca el Bearer.
        assertThat(DENEGACIONES).singleElement().satisfies(d -> {
            assertThat(d.motivo()).isEqualTo("TOKEN_NO_APLICABLE");
            assertThat(d.actorTipo()).isEqualTo("MODULO");
            assertThat(d.actorId()).isEqualTo("modulo-productos");
            assertThat(d.jti()).isEqualTo(jti);
            assertThat(d.metodo()).isEqualTo("GET");
            assertThat(d.ruta()).isEqualTo("/api/v1/prueba/persona");
        });
        assertThat(DENEGACIONES.toString()).doesNotContain(servicio);
    }

    @Test
    void siLaAuditoriaFallaElTokenNoAplicableSigueSiendo403() throws Exception {
        String servicio = tokenDe("modulo-productos");
        FALLA_AUDITORIA.set(true);
        try {
            HttpResponse<String> respuesta = get("/api/v1/prueba/persona", servicio);

            assertThat(respuesta.statusCode()).isEqualTo(403);
            assertThat(json.readTree(respuesta.body()).get("code").asText()).isEqualTo("TOKEN_NO_APLICABLE");
        } finally {
            FALLA_AUDITORIA.set(false);
        }
    }

    // ── Catálogo (SPEC-18 ESC-18.7) ──────────────────────────

    @Test
    void conRolesLeerSeConsultaElCatalogo() throws Exception {
        String token = tokenDe("modulo-productos");

        HttpResponse<String> roles = get("/api/v1/roles", token);
        HttpResponse<String> permisos = get("/api/v1/permisos?modulo=seguridad", token);
        HttpResponse<String> sinScope = get("/api/v1/roles", tokenDe("modulo-ventas"));

        assertThat(roles.statusCode()).isEqualTo(200);
        JsonNode vendedor = json.readTree(roles.body()).get("roles").get(1);
        assertThat(vendedor.get("codigo").asText()).isEqualTo("VENDEDOR");
        assertThat(vendedor.get("permisos")).extracting(JsonNode::asText)
                .containsExactly("cliente.buscar", "cliente.registrar");
        assertThat(permisos.statusCode()).isEqualTo(200);
        assertThat(json.readTree(permisos.body()).get("permisos").get(0).get("modulo").asText()).isEqualTo("seguridad");
        assertThat(sinScope.statusCode()).isEqualTo(403);
    }

    // ── Utilidades ───────────────────────────────────────────

    private String tokenDe(String cliente) throws Exception {
        return json.readTree(token(cliente, SECRETO, null).body()).get("accessToken").asText();
    }

    private HttpResponse<String> token(String cliente, String secreto, String scope) throws Exception {
        String cuerpo = "grant_type=client_credentials&client_id=" + enc(cliente) + "&client_secret=" + enc(secreto)
                + (scope == null ? "" : "&scope=" + enc(scope));
        return post("/api/v1/auth/token", null, "application/x-www-form-urlencoded", cuerpo);
    }

    private HttpResponse<String> introspeccionar(String bearer, String token) throws Exception {
        return post("/api/v1/auth/introspeccion", bearer, "application/json",
                json.writeValueAsString(Map.of("token", token)));
    }

    private HttpResponse<String> get(String ruta, String bearer) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(BASE + ruta)).GET();
        if (bearer != null) {
            peticion.header("Authorization", "Bearer " + bearer);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String ruta, String bearer, String tipo, String cuerpo) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(BASE + ruta))
                .header("Content-Type", tipo)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (bearer != null) {
            peticion.header("Authorization", "Bearer " + bearer);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String enc(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private static int puertoLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
