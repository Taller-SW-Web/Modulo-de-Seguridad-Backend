package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.ClienteTecnico;
import com.marketplace.auth_service.identidad.application.dto.ResultadoGestionCliente;
import com.marketplace.auth_service.identidad.application.dto.ScopeAsignado;
import com.marketplace.auth_service.identidad.application.dto.ScopeDisponible;
import com.marketplace.auth_service.identidad.application.dto.SecretoEntregado;
import com.marketplace.auth_service.identidad.application.port.out.GobiernoClientesPort;
import com.marketplace.auth_service.identidad.application.port.out.RegistroClientesPort;
import com.marketplace.auth_service.identidad.domain.model.SecretoCliente;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** SPEC-19 sin base: reglas, orden de decisiones y auditoría (ESC-19.1 a ESC-19.9). */
class GestionClientesTecnicosServiceTest {

    static final ActorUsuario ADMIN = new ActorUsuario(UUID.randomUUID(), UUID.randomUUID(),
            Set.of("ADMIN_SISTEMA"), Set.of("integracion.gestionar"));
    static final ContextoPeticion CONTEXTO = new ContextoPeticion("10.0.0.1", "prueba", UUID.randomUUID());
    static final Map<String, String> CATALOGO = Map.of("usuarios:leer", "api-seguridad",
            "roles:leer", "api-seguridad", "cotizaciones:calcular", "api-despacho");

    Registro registro;
    Gobierno gobierno;
    GestionClientesTecnicosService servicio;
    int generados;

    @BeforeEach
    void preparar() {
        registro = new Registro();
        gobierno = new Gobierno();
        servicio = new GestionClientesTecnicosService(registro, gobierno,
                Clock.fixed(Instant.parse("2026-11-10T20:00:00Z"), ZoneOffset.UTC), () -> "secreto-" + (++generados));
    }

    @Test
    void esc19_1_altaGuardaSoloElHashYEntregaElSecretoUnaVez() {
        var resultado = servicio.crear(ADMIN, "modulo-prueba-h5", " Cliente de prueba H5 ", List.of("usuarios:leer"),
                CONTEXTO);

        SecretoEntregado entregado = valor(resultado);
        assertThat(entregado.clientSecret()).isEqualTo("secreto-1");
        assertThat(entregado.cliente()).isEqualTo(new ClienteTecnico("modulo-prueba-h5", "Cliente de prueba H5", true,
                List.of(new ScopeAsignado("usuarios:leer", "api-seguridad"))));
        assertThat(registro.hashes.get("modulo-prueba-h5")).isEqualTo(SecretoCliente.hash("secreto-1"));
        assertThat(entregado.toString()).doesNotContain("secreto-1");
        assertThat(gobierno.acciones).containsExactly("CLIENTE_SERVICIO_CREADO");
        assertThat(gobierno.detalles.get(0)).containsEntry("clientId", "modulo-prueba-h5")
                .containsEntry("scopes", "usuarios:leer");
    }

    @Test
    void elSecretoGeneradoTiene256BitsEnBase64Url() {
        String secreto = GestionClientesTecnicosService.secretoAleatorio();

        assertThat(secreto).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(secreto)).hasSize(32);
        assertThat(GestionClientesTecnicosService.secretoAleatorio()).isNotEqualTo(secreto);
    }

    @Test
    void esc19_2_unClientIdExistenteEs409YNoCambiaSuSecreto() {
        registro.agregar("modulo-marketplace", true);
        String hashPrevio = registro.hashes.get("modulo-marketplace");

        var resultado = servicio.crear(ADMIN, "modulo-marketplace", "Otro", List.of(), CONTEXTO);

        assertThat(codigo(resultado)).isEqualTo("CLIENTE_SERVICIO_EXISTENTE");
        assertThat(registro.hashes.get("modulo-marketplace")).isEqualTo(hashPrevio);
        assertThat(gobierno.acciones).isEmpty();
    }

    @Test
    void esc19_3_unScopeFueraDelCatalogoNoCreaNada() {
        var resultado = servicio.crear(ADMIN, "modulo-nuevo", "Nuevo", List.of("usuarios:leer", "pagos:cobrar"),
                CONTEXTO);

        assertThat(resultado).isEqualTo(new ResultadoGestionCliente.Rechazado<>("VALIDACION", "scopes"));
        assertThat(registro.activos).doesNotContainKey("modulo-nuevo");
        assertThat(gobierno.acciones).isEmpty();
    }

    @Test
    void unScopeRepetidoEnElAltaEsValidacion() {
        var resultado = servicio.crear(ADMIN, "modulo-nuevo", "Nuevo", List.of("roles:leer", "roles:leer"), CONTEXTO);

        assertThat(resultado).isEqualTo(new ResultadoGestionCliente.Rechazado<>("VALIDACION", "scopes"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "Modulo", "-modulo", "modulo_x", "modulo con espacio",
            "un-identificador-de-cliente-que-pasa-de-cincuenta-c"})
    void rf19_5_clientIdInvalido(String clientId) {
        assertThat(servicio.crear(ADMIN, clientId, "Nombre", List.of(), CONTEXTO))
                .isEqualTo(new ResultadoGestionCliente.Rechazado<>("VALIDACION", "clientId"));
    }

    @Test
    void rf19_5_nombreVacioOLargoEsValidacion() {
        assertThat(servicio.crear(ADMIN, "modulo-x1", " ", List.of(), CONTEXTO))
                .isEqualTo(new ResultadoGestionCliente.Rechazado<>("VALIDACION", "nombre"));
        assertThat(servicio.crear(ADMIN, "modulo-x1", "n".repeat(101), List.of(), CONTEXTO))
                .isEqualTo(new ResultadoGestionCliente.Rechazado<>("VALIDACION", "nombre"));
    }

    @Test
    void esc19_4_concederYRevocarSoloAuditanSiCambian() {
        registro.agregar("modulo-prueba", true);

        assertThat(servicio.conceder(ADMIN, "modulo-prueba", "roles:leer", CONTEXTO))
                .isInstanceOf(ResultadoGestionCliente.Correcto.class);
        assertThat(servicio.conceder(ADMIN, "modulo-prueba", "roles:leer", CONTEXTO))
                .as("ESC-19.5: idempotente").isInstanceOf(ResultadoGestionCliente.Correcto.class);
        assertThat(servicio.revocar(ADMIN, "modulo-prueba", "roles:leer", CONTEXTO))
                .isInstanceOf(ResultadoGestionCliente.Correcto.class);
        assertThat(servicio.revocar(ADMIN, "modulo-prueba", "roles:leer", CONTEXTO))
                .isInstanceOf(ResultadoGestionCliente.Correcto.class);

        assertThat(gobierno.acciones).containsExactly("SCOPE_CONCEDIDO", "SCOPE_REVOCADO");
        assertThat(gobierno.detalles.get(0)).containsEntry("scope", "roles:leer");
        assertThat(registro.scopes.get("modulo-prueba")).isEmpty();
    }

    @Test
    void rf19_10_concederUnScopeFueraDelCatalogoEsValidacion() {
        registro.agregar("modulo-prueba", true);

        assertThat(servicio.conceder(ADMIN, "modulo-prueba", "pagos:cobrar", CONTEXTO))
                .isEqualTo(new ResultadoGestionCliente.Rechazado<>("VALIDACION", "scope"));
        assertThat(gobierno.acciones).isEmpty();
    }

    @Test
    void esc19_6_rotarReemplazaElHashYNoAuditaElSecreto() {
        registro.agregar("modulo-prueba", true);

        SecretoEntregado nuevo = valor(servicio.rotarSecreto(ADMIN, "modulo-prueba", CONTEXTO));

        assertThat(SecretoCliente.coincide(nuevo.clientSecret(), registro.hashes.get("modulo-prueba"))).isTrue();
        assertThat(SecretoCliente.coincide("s-modulo-prueba", registro.hashes.get("modulo-prueba"))).isFalse();
        assertThat(gobierno.acciones).containsExactly("SECRETO_SERVICIO_ROTADO");
        assertThat(gobierno.detalles.get(0)).containsOnlyKeys("clientId");
        assertThat(gobierno.detalles.get(0).values())
                .noneMatch(v -> v.contains(nuevo.clientSecret()) || v.contains(registro.hashes.get("modulo-prueba")));
    }

    @Test
    void esc19_7_desactivarYReactivarSonIdempotentes() {
        registro.agregar("modulo-prueba", true);

        servicio.cambiarEstado(ADMIN, "modulo-prueba", false, CONTEXTO);
        servicio.cambiarEstado(ADMIN, "modulo-prueba", false, CONTEXTO);
        assertThat(registro.activos.get("modulo-prueba")).isFalse();
        servicio.cambiarEstado(ADMIN, "modulo-prueba", true, CONTEXTO);

        assertThat(registro.activos.get("modulo-prueba")).isTrue();
        assertThat(gobierno.acciones).containsExactly("CLIENTE_SERVICIO_DESACTIVADO", "CLIENTE_SERVICIO_REACTIVADO");
    }

    @Test
    void rf19_11_clienteInexistenteEs404EnCadaOperacion() {
        assertThat(codigo(servicio.rotarSecreto(ADMIN, "modulo-fantasma", CONTEXTO))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(servicio.cambiarEstado(ADMIN, "modulo-fantasma", false, CONTEXTO))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(servicio.conceder(ADMIN, "modulo-fantasma", "roles:leer", CONTEXTO))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(servicio.revocar(ADMIN, "modulo-fantasma", "roles:leer", CONTEXTO))).isEqualTo("NO_ENCONTRADO");
        assertThat(codigo(servicio.rotarSecreto(ADMIN, "' or '1'='1", CONTEXTO))).isEqualTo("NO_ENCONTRADO");
    }

    @Test
    void esc19_8_sinPermisoSeRechazaAntesDeMirarSiExisteYSeRegistraLaDenegacion() {
        gobierno.permitir = false;
        registro.agregar("modulo-prueba", true);

        assertThat(codigo(servicio.listar(ADMIN, CONTEXTO))).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(codigo(servicio.catalogo(ADMIN, CONTEXTO))).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(codigo(servicio.crear(ADMIN, "x", null, null, CONTEXTO))).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(codigo(servicio.rotarSecreto(ADMIN, "modulo-prueba", CONTEXTO))).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(codigo(servicio.rotarSecreto(ADMIN, "modulo-fantasma", CONTEXTO))).isEqualTo("SCOPE_INSUFICIENTE");
        assertThat(codigo(servicio.conceder(ADMIN, "modulo-prueba", "roles:leer", CONTEXTO)))
                .isEqualTo("SCOPE_INSUFICIENTE");

        assertThat(registro.consultas).isZero();
        assertThat(gobierno.denegaciones).isEqualTo(6);
        assertThat(gobierno.acciones).isEmpty();
    }

    @Test
    void listarYCatalogoDevuelvenLoDelRegistro() {
        registro.agregar("modulo-b", true);
        registro.agregar("modulo-a", false);

        assertThat(valor(servicio.listar(ADMIN, CONTEXTO))).extracting(ClienteTecnico::clientId)
                .containsExactly("modulo-a", "modulo-b");
        assertThat(valor(servicio.catalogo(ADMIN, CONTEXTO))).hasSize(3);
    }

    private static <T> T valor(ResultadoGestionCliente<T> resultado) {
        assertThat(resultado).isInstanceOf(ResultadoGestionCliente.Correcto.class);
        return ((ResultadoGestionCliente.Correcto<T>) resultado).valor();
    }

    private static String codigo(ResultadoGestionCliente<?> resultado) {
        assertThat(resultado).isInstanceOf(ResultadoGestionCliente.Rechazado.class);
        return ((ResultadoGestionCliente.Rechazado<?>) resultado).codigo();
    }

    static final class Gobierno implements GobiernoClientesPort {
        boolean permitir = true;
        int denegaciones;
        final List<String> acciones = new ArrayList<>();
        final List<Map<String, String>> detalles = new ArrayList<>();

        @Override public boolean autorizado(ActorUsuario actor) { return permitir; }

        @Override public void registrar(String accion, ActorUsuario actor, ContextoPeticion contexto, Instant fecha,
                                        Map<String, String> detalle) {
            acciones.add(accion);
            detalles.add(Map.copyOf(detalle));
        }

        @Override public void denegado(ActorUsuario actor, ContextoPeticion contexto, Instant fecha) {
            denegaciones++;
        }
    }

    static final class Registro implements RegistroClientesPort {
        final Map<String, Boolean> activos = new TreeMap<>();
        final Map<String, String> nombres = new HashMap<>();
        final Map<String, String> hashes = new HashMap<>();
        final Map<String, Set<String>> scopes = new HashMap<>();
        int consultas;

        void agregar(String clientId, boolean activo) {
            activos.put(clientId, activo);
            nombres.put(clientId, clientId);
            hashes.put(clientId, SecretoCliente.hash("s-" + clientId));
            scopes.put(clientId, new LinkedHashSet<>());
        }

        @Override public List<ClienteTecnico> listar() {
            consultas++;
            return activos.keySet().stream().map(id -> buscar(id).orElseThrow()).toList();
        }

        @Override public Optional<ClienteTecnico> buscar(String clientId) {
            consultas++;
            if (!activos.containsKey(clientId)) return Optional.empty();
            return Optional.of(new ClienteTecnico(clientId, nombres.get(clientId), activos.get(clientId),
                    scopes.get(clientId).stream().sorted().map(s -> new ScopeAsignado(s, CATALOGO.get(s))).toList()));
        }

        @Override public List<ScopeDisponible> catalogo() {
            consultas++;
            return CATALOGO.entrySet().stream().map(e -> new ScopeDisponible(e.getKey(), e.getValue(), null)).toList();
        }

        @Override public Set<String> catalogados(Collection<String> codigos) {
            consultas++;
            return codigos.stream().filter(CATALOGO::containsKey).collect(Collectors.toSet());
        }

        @Override public boolean insertar(String clientId, String nombre, String secretoHash) {
            consultas++;
            if (activos.containsKey(clientId)) return false;
            agregar(clientId, true);
            nombres.put(clientId, nombre);
            hashes.put(clientId, secretoHash);
            return true;
        }

        @Override public Optional<Boolean> bloquear(String clientId) {
            consultas++;
            return Optional.ofNullable(activos.get(clientId));
        }

        @Override public void actualizarSecreto(String clientId, String secretoHash) { hashes.put(clientId, secretoHash); }

        @Override public void actualizarActivo(String clientId, boolean activo) { activos.put(clientId, activo); }

        @Override public boolean conceder(String clientId, String scope) { return scopes.get(clientId).add(scope); }

        @Override public boolean revocar(String clientId, String scope) { return scopes.get(clientId).remove(scope); }
    }
}
