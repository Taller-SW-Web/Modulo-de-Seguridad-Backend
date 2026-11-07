package com.marketplace.auth_service.usuario.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAdministracionCuenta;
import com.marketplace.auth_service.usuario.application.port.out.EfectosBajaReactivacionPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FEAT-02 con dobles: permisos, protección del último administrador, idempotencia y orden de efectos. */
class BajaReactivacionServiceTest {

    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.7", "pruebas", UUID.randomUUID());
    private static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID OTRO_ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    private static final UUID CLIENTE = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final ActorUsuario ACTOR = new ActorUsuario(ADMIN, UUID.randomUUID(), Set.of("ADMIN_SISTEMA"), Set.of());

    private final Map<UUID, CuentaSnapshot> cuentas = new HashMap<>();
    private final Set<UUID> administradores = new HashSet<>(Set.of(ADMIN));
    private final Set<Permiso> permisos = new HashSet<>(Set.of(Permiso.USUARIO_DESACTIVAR, Permiso.USUARIO_REACTIVAR));
    private final List<String> pasos = new ArrayList<>();
    private BajaReactivacionService servicio;

    private static CuentaSnapshot cuenta(UUID id, String estado, boolean verificado) {
        return new CuentaSnapshot(id, "Nombre", "Apellido", id + "@g7.test", null, estado,
                estado, verificado, false, false, 0, 0);
    }

    @BeforeEach
    void preparar() {
        cuentas.put(ADMIN, cuenta(ADMIN, "ACTIVO", true));
        cuentas.put(OTRO_ADMIN, cuenta(OTRO_ADMIN, "ACTIVO", true));
        cuentas.put(CLIENTE, cuenta(CLIENTE, "ACTIVO", true));
        servicio = new BajaReactivacionService(new Repositorio(), (id, estado, ahora) -> {
            pasos.add("estado:" + estado);
            var c = cuentas.get(id);
            cuentas.put(id, cuenta(id, estado.name(), c.correoVerificado()));
        }, new Efectos(), Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void bajaPasaAInactivoRevocaSesionesYAuditaEnOrden() {
        assertThat(servicio.darDeBaja(ACTOR, CLIENTE.toString(), CONTEXTO)).isEqualTo(ResultadoAdministracionCuenta.HECHO);
        assertThat(pasos).containsExactly("catalogo", "lock:" + ADMIN, "lock:" + CLIENTE, "estado:INACTIVO",
                "sesiones:" + CLIENTE, "baja:" + CLIENTE);
    }

    @Test
    void nadieSeDaDeBajaASiMismoNiAlUltimoAdministradorActivo() {
        assertThat(servicio.darDeBaja(ACTOR, ADMIN.toString(), CONTEXTO))
                .isEqualTo(ResultadoAdministracionCuenta.ADMINISTRADOR_PROTEGIDO);

        administradores.add(OTRO_ADMIN);
        cuentas.put(ADMIN, cuenta(ADMIN, "ACTIVO", true));
        // OTRO_ADMIN no es el último: ADMIN sigue activo.
        assertThat(servicio.darDeBaja(ACTOR, OTRO_ADMIN.toString(), CONTEXTO)).isEqualTo(ResultadoAdministracionCuenta.HECHO);
        assertThat(pasos).doesNotContain("estado:INACTIVO:" + ADMIN);
    }

    @Test
    void ultimoAdministradorActivoQuedaProtegidoAunqueLoIntenteOtro() {
        administradores.add(OTRO_ADMIN);
        cuentas.put(ADMIN, cuenta(ADMIN, "ACTIVO", true));
        // Visto desde OTRO_ADMIN como actor y ADMIN inactivo: OTRO_ADMIN es el único activo.
        var otro = new ActorUsuario(OTRO_ADMIN, UUID.randomUUID(), Set.of("ADMIN_SISTEMA"), Set.of());
        cuentas.put(ADMIN, cuenta(ADMIN, "INACTIVO", true));
        assertThat(servicio.darDeBaja(otro, OTRO_ADMIN.toString(), CONTEXTO))
                .isEqualTo(ResultadoAdministracionCuenta.ADMINISTRADOR_PROTEGIDO);
        cuentas.put(ADMIN, cuenta(ADMIN, "ACTIVO", true));
        administradores.remove(ADMIN);
        cuentas.put(CLIENTE, cuenta(CLIENTE, "ACTIVO", true));
        assertThat(servicio.darDeBaja(otro, CLIENTE.toString(), CONTEXTO)).isEqualTo(ResultadoAdministracionCuenta.HECHO);
        assertThat(pasos).doesNotContain("sesiones:" + OTRO_ADMIN);
    }

    @Test
    void sinPermisoIdMalformadoOInexistenteNoTocanNada() {
        assertThat(servicio.darDeBaja(ACTOR, "no-es-uuid", CONTEXTO)).isEqualTo(ResultadoAdministracionCuenta.VALIDACION);
        assertThat(servicio.darDeBaja(ACTOR, UUID.randomUUID().toString(), CONTEXTO))
                .isEqualTo(ResultadoAdministracionCuenta.NO_ENCONTRADO);
        permisos.clear();
        assertThat(servicio.darDeBaja(ACTOR, CLIENTE.toString(), CONTEXTO)).isEqualTo(ResultadoAdministracionCuenta.SIN_PERMISO);
        assertThat(servicio.reactivar(ACTOR, CLIENTE.toString(), CONTEXTO)).isEqualTo(ResultadoAdministracionCuenta.SIN_PERMISO);
        assertThat(pasos).noneMatch(p -> p.startsWith("estado") || p.startsWith("sesiones") || p.startsWith("baja"))
                .contains("denegado", "denegado");
    }

    @Test
    void bajaDeUnaCuentaYaInactivaNoRepiteEfectos() {
        cuentas.put(CLIENTE, cuenta(CLIENTE, "INACTIVO", true));
        assertThat(servicio.darDeBaja(ACTOR, CLIENTE.toString(), CONTEXTO)).isEqualTo(ResultadoAdministracionCuenta.HECHO);
        assertThat(pasos).noneMatch(p -> p.startsWith("estado") || p.startsWith("baja"));
    }

    @Test
    void reactivaInactivaAActivoConEventoYNoTocaLaQueNoEstaDeBaja() {
        cuentas.put(CLIENTE, cuenta(CLIENTE, "INACTIVO", true));
        assertThat(servicio.reactivar(ACTOR, CLIENTE.toString(), CONTEXTO)).isEqualTo(ResultadoAdministracionCuenta.HECHO);
        assertThat(pasos).containsExactly("lock:" + CLIENTE, "estado:ACTIVO", "reactivacion:" + CLIENTE);

        pasos.clear();
        // RF-04.5: sobre una cuenta ACTIVO responde igual, sin cambio ni evento.
        assertThat(servicio.reactivar(ACTOR, CLIENTE.toString(), CONTEXTO)).isEqualTo(ResultadoAdministracionCuenta.HECHO);
        assertThat(pasos).containsExactly("lock:" + CLIENTE);
    }

    @Test
    void reactivarUnaCuentaSinCorreoVerificadoLaDejaPendiente() {
        cuentas.put(CLIENTE, cuenta(CLIENTE, "INACTIVO", false));
        servicio.reactivar(ACTOR, CLIENTE.toString(), CONTEXTO);
        assertThat(pasos).contains("estado:PENDIENTE_VERIFICACION");
    }

    private final class Repositorio implements UsuarioRepositoryPort {
        @Override public Optional<CuentaSnapshot> findByCorreoNormalizado(String correo) { return Optional.empty(); }
        @Override public Optional<CuentaSnapshot> findById(UUID id) { return Optional.ofNullable(cuentas.get(id)); }
        @Override public CuentaSnapshot lockById(UUID id) {
            pasos.add("lock:" + id);
            return cuentas.get(id);
        }
        @Override public long countActiveByIds(Set<UUID> ids) {
            return ids.stream().filter(id -> cuentas.containsKey(id) && "ACTIVO".equals(cuentas.get(id).estadoBase())).count();
        }
        @Override public Map<UUID, CuentaSnapshot> findAllByIds(Set<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public PaginaResultado<CuentaSnapshot> findByFiltro(FiltroCuenta f, Paginacion p, Set<UUID> ids) {
            throw new UnsupportedOperationException();
        }
    }

    private final class Efectos implements EfectosBajaReactivacionPort {
        @Override public boolean autorizado(UUID actor, Permiso permiso) { return permisos.contains(permiso); }
        @Override public void bloquearCatalogoAdministrador() { pasos.add("catalogo"); }
        @Override public boolean esAdministrador(UUID id) { return administradores.contains(id); }
        @Override public Set<UUID> administradores() { return Set.copyOf(administradores); }
        @Override public void revocarSesiones(UUID id) { pasos.add("sesiones:" + id); }
        @Override public void registrarBaja(UUID id, ActorUsuario actor, ContextoPeticion c, Instant a) { pasos.add("baja:" + id); }
        @Override public void registrarReactivacion(UUID id, ActorUsuario actor, ContextoPeticion c, Instant a) {
            pasos.add("reactivacion:" + id);
        }
        @Override public void denegado(ActorUsuario actor, ContextoPeticion c, Instant a) { pasos.add("denegado"); }
    }
}
