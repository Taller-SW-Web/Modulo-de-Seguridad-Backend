package com.marketplace.auth_service.usuario.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.configuration.IdentidadConfig;
import com.marketplace.auth_service.configuration.SeguridadConfig;
import com.marketplace.auth_service.identidad.ClavesDePrueba;
import com.marketplace.auth_service.identidad.TokensDePrueba;
import com.marketplace.auth_service.identidad.application.dto.DocumentoIdentidad;
import com.marketplace.auth_service.identidad.application.port.in.ConsultaIdentidadPort;
import com.marketplace.auth_service.identidad.application.port.out.AuditoriaIdentidadPort;
import com.marketplace.auth_service.identidad.application.port.out.DocumentoIdentidadPort;
import com.marketplace.auth_service.identidad.application.port.out.RolesIdentidadPort;
import com.marketplace.auth_service.identidad.application.service.ConsultaIdentidadService;
import com.marketplace.auth_service.identidad.adapter.out.capacidad.CuentasIdentidadAdapter;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.ConsultaIdentidadServicioAdapter;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * {@code POST /api/v1/usuarios/lote} con el filtro de acceso real (SPEC-18 RF-18.2,
 * FEAT-06-T2). Cuentas, roles, documento y auditoría son dobles en memoria; prueban
 * contrato HTTP, el límite de 100, duplicados e identificadores malformados.
 */
@SpringBootTest(classes = UsuariosLoteHttpTest.Aplicacion.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsuariosLoteHttpTest {

    private static final String ISSUER = "https://seguridad.ejemplo.test/api/v1/auth";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC);

    private static final Map<UUID, CuentaSnapshot> CUENTAS = new HashMap<>();
    private static final List<UUID> AUDITADOS = new ArrayList<>();
    private static final List<UUID> CON_DOCUMENTO_CLARO = new ArrayList<>();
    private static final Set<UUID> CON_DOCUMENTO = new java.util.HashSet<>();

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class, RabbitAutoConfiguration.class})
    @Import({IdentidadConfig.class, SeguridadConfig.class, UsuariosController.class, UsuariosErroresAdvice.class})
    static class Aplicacion {

        @Bean
        ConsultaUsuariosPort consultaUsuariosPort() {
            ConsultaIdentidadPort identidad = new ConsultaIdentidadService(new CuentasIdentidadAdapter(cuentas()),
                    roles(), documentos(), auditoria(), RELOJ);
            return new ConsultaUsuariosService(cuentas(), autorizacion(), bloqueos(),
                    new ConsultaIdentidadServicioAdapter(identidad));
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
                public Map<UUID, CuentaSnapshot> leerLote(Set<UUID> ids) {
                    Map<UUID, CuentaSnapshot> encontradas = new HashMap<>();
                    ids.forEach(id -> {
                        CuentaSnapshot c = CUENTAS.get(id);
                        if (c != null) {
                            encontradas.put(id, c);
                        }
                    });
                    return encontradas;
                }

                @Override
                public PaginaResultado<CuentaSnapshot> consultar(FiltroCuenta filtro, Paginacion paginacion) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        private static RolesIdentidadPort roles() {
            return id -> List.of("CLIENTE");
        }

        private static DocumentoIdentidadPort documentos() {
            return new DocumentoIdentidadPort() {
                @Override
                public Optional<DocumentoIdentidad> proyectar(UUID usuarioId) {
                    return CON_DOCUMENTO.contains(usuarioId)
                            ? Optional.of(new DocumentoIdentidad("DNI", "*****234")) : Optional.empty();
                }

                @Override
                public Optional<DocumentoIdentidad> leerEnClaro(UUID usuarioId) {
                    return CON_DOCUMENTO.contains(usuarioId)
                            ? Optional.of(new DocumentoIdentidad("DNI", "12345234")) : Optional.empty();
                }
            };
        }

        private static AuditoriaIdentidadPort auditoria() {
            return new AuditoriaIdentidadPort() {
                @Override
                public void moduloConsultoUsuario(String clientId, UUID objetivo, Instant fecha) {
                    AUDITADOS.add(objetivo);
                }

                @Override
                public void moduloObtuvoDocumento(String clientId, UUID objetivo, Instant fecha) {
                    CON_DOCUMENTO_CLARO.add(objetivo);
                }
            };
        }

        private static AutorizacionUsuariosPort autorizacion() {
            return new AutorizacionUsuariosPort() {
                @Override
                public void exigirVerUsuarios(com.marketplace.auth_service.shared.domain.ActorUsuario actor) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Map<UUID, RolesDeCuenta> rolesDe(Set<UUID> ids) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        private static BloqueosConsultaPort bloqueos() {
            return ids -> Map.of();
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

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper json = new ObjectMapper();

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID LUIS = UUID.randomUUID();

    @BeforeEach
    void reiniciar() {
        CUENTAS.clear();
        CUENTAS.put(ANA, new CuentaSnapshot(ANA, "Ana", "Pérez", "ana@clientes.test", null, "ACTIVO", "ACTIVO",
                true, false, false, 0, 0));
        CUENTAS.put(LUIS, new CuentaSnapshot(LUIS, "Luis", "Gómez", "luis@clientes.test", null, "ACTIVO", "ACTIVO",
                true, false, false, 0, 0));
        AUDITADOS.clear();
        CON_DOCUMENTO_CLARO.clear();
        CON_DOCUMENTO.clear();
    }

    private HttpResponse<String> lote(String cuerpoJson, String bearer) throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/usuarios/lote"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpoJson));
        if (bearer != null) {
            peticion.header("Authorization", "Bearer " + bearer);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String tokenServicio() {
        return TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER).build());
    }

    private String tokenServicioConDocumento() {
        return TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER)
                .claim("scope", "usuarios:leer usuarios:leer:documento").build());
    }

    private static String idsJson(List<UUID> ids) {
        return "{\"ids\":[" + ids.stream().map(id -> "\"" + id + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]}";
    }

    private JsonNode problema(HttpResponse<String> respuesta) throws IOException {
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                v -> assertThat(v).startsWith("application/problem+json"));
        return json.readTree(respuesta.body());
    }

    // ── ESC-18.2 ──────────────────────────────────────────────

    @Test
    void devuelveEncontradosYNoHalladosPorSeparadoSinFallarEntera() throws Exception {
        UUID inexistente = UUID.randomUUID();
        HttpResponse<String> respuesta = lote(idsJson(List.of(ANA, LUIS, inexistente)), tokenServicio());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("usuarios")).hasSize(2);
        assertThat(cuerpo.get("noEncontrados")).hasSize(1);
        assertThat(cuerpo.get("noEncontrados").get(0).asText()).isEqualTo(inexistente.toString());
        assertThat(AUDITADOS).containsExactlyInAnyOrder(ANA, LUIS);
    }

    @Test
    void unUsuarioNoViajaConElHashNiAtributosPrivados() throws Exception {
        HttpResponse<String> respuesta = lote(idsJson(List.of(ANA)), tokenServicio());

        assertThat(respuesta.body()).doesNotContain("password", "hash", "intentosFallidos", "bloqueosSeguidos");
    }

    @Test
    void sinElScopeDeDocumentoLoDevuelveEnmascarado() throws Exception {
        CON_DOCUMENTO.add(ANA);
        HttpResponse<String> respuesta = lote(idsJson(List.of(ANA)), tokenServicio());

        JsonNode usuario = json.readTree(respuesta.body()).get("usuarios").get(0);
        assertThat(usuario.get("documentoEnmascarado").asText()).isEqualTo("*****234");
        assertThat(usuario.has("documento")).isFalse();
        assertThat(CON_DOCUMENTO_CLARO).isEmpty();
    }

    @Test
    void conElScopeDeDocumentoLoDevuelveEnClaroYAudita() throws Exception {
        CON_DOCUMENTO.add(ANA);
        HttpResponse<String> respuesta = lote(idsJson(List.of(ANA)), tokenServicioConDocumento());

        JsonNode usuario = json.readTree(respuesta.body()).get("usuarios").get(0);
        assertThat(usuario.get("documento").asText()).isEqualTo("12345234");
        assertThat(usuario.has("documentoEnmascarado")).isFalse();
        assertThat(CON_DOCUMENTO_CLARO).containsExactly(ANA);
    }

    // ── ESC-18.3 ──────────────────────────────────────────────

    @Test
    void masDeCienIdentificadoresResponde400LoteDemasiadoGrande() throws Exception {
        List<UUID> muchos = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            muchos.add(UUID.randomUUID());
        }

        HttpResponse<String> respuesta = lote(idsJson(muchos), tokenServicio());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        JsonNode problema = problema(respuesta);
        assertThat(problema.get("code").asText()).isEqualTo("LOTE_DEMASIADO_GRANDE");
        assertThat(problema.get("detail").asText()).contains("101").contains("100");
        assertThat(AUDITADOS).isEmpty();
    }

    @Test
    void unIdentificadorMalformadoResponde400ValidacionConSuIndice() throws Exception {
        String cuerpo = "{\"ids\":[\"" + ANA + "\",\"no-es-uuid\"]}";

        HttpResponse<String> respuesta = lote(cuerpo, tokenServicio());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        JsonNode problema = problema(respuesta);
        assertThat(problema.get("code").asText()).isEqualTo("VALIDACION");
        assertThat(problema.get("errores").get(0).get("campo").asText()).isEqualTo("ids[1]");
        assertThat(AUDITADOS).isEmpty();
    }

    @Test
    void duplicadosSeColapsanAUnaSolaAparicion() throws Exception {
        HttpResponse<String> respuesta = lote(idsJson(List.of(ANA, ANA, ANA)), tokenServicio());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(respuesta.body());
        assertThat(cuerpo.get("usuarios")).hasSize(1);
        assertThat(AUDITADOS).containsExactly(ANA);
    }

    @Test
    void unaListaVaciaResponde400Validacion() throws Exception {
        HttpResponse<String> respuesta = lote("{\"ids\":[]}", tokenServicio());

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(problema(respuesta).get("errores").get(0).get("campo").asText()).isEqualTo("ids");
    }

    // ── Reglas comunes de SPEC-17 ─────────────────────────────

    @Test
    void sinTokenResponde401() throws Exception {
        HttpResponse<String> respuesta = lote(idsJson(List.of(ANA)), null);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("TOKEN_INVALIDO");
    }

    @Test
    void unTokenDeServicioSinElScopeDeUsuariosLeerResponde403ScopeInsuficiente() throws Exception {
        String sinScope = TokensDePrueba.firmarRs256(TokensDePrueba.servicioValido(ISSUER)
                .claim("scope", "direcciones:leer").build());

        HttpResponse<String> respuesta = lote(idsJson(List.of(ANA)), sinScope);

        assertThat(respuesta.statusCode()).isEqualTo(403);
        assertThat(problema(respuesta).get("code").asText()).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(AUDITADOS).isEmpty();
    }
}
