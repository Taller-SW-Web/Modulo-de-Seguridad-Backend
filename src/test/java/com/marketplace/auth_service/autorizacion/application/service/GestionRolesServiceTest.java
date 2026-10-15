package com.marketplace.auth_service.autorizacion.application.service;

import com.marketplace.auth_service.autorizacion.application.dto.CambioRolCommand;
import com.marketplace.auth_service.autorizacion.application.dto.CuentaRoles;
import com.marketplace.auth_service.autorizacion.application.dto.RegistroRol;
import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiados;
import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiadosEvento;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AsociacionesRolPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AuditoriaRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.CuentasRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.EventosRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.SesionesRolesPort;
import com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException;
import com.marketplace.auth_service.autorizacion.domain.exception.AdministradorProtegidoException;
import com.marketplace.auth_service.autorizacion.domain.exception.GestionRolesNoDisponibleException;
import com.marketplace.auth_service.autorizacion.domain.exception.RolInvalidoException;
import com.marketplace.auth_service.autorizacion.domain.exception.UsuarioNoEncontradoException;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coordinador de asignación de roles con dobles en memoria (EP-05-T003): orden
 * de locks, autorización antes de todo, reautorización bajo lock y efectos.
 * La atomicidad real (rollback) se prueba sobre PostgreSQL en
 * {@code GestionRolesPostgresTest}.
 */
class GestionRolesServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-13T15:00:00Z");
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.7", "prueba", UUID.randomUUID());

    /** Registro ordenado de lo que hace el coordinador, para comprobar el orden de §3. */
    private final List<String> llamadas = new ArrayList<>();
    private final Map<UUID, Set<Rol>> roles = new HashMap<>();
    private final Map<UUID, CuentaRoles> cuentasExistentes = new HashMap<>();
    private final List<RegistroRol> auditados = new ArrayList<>();
    private final List<RolesCambiadosEvento> eventos = new ArrayList<>();
    /** Si no es null, se ejecuta al tomar el lock del catálogo: simula otra transacción que ganó antes. */
    private Runnable alBloquearCatalogo;
    private RuntimeException falloAuditoria;

    private final AsociacionesRolPort asociaciones = new AsociacionesRolPort() {
        @Override
        public Set<Rol> rolesDe(UUID usuarioId) {
            return Set.copyOf(roles.getOrDefault(usuarioId, Set.of()));
        }

        @Override
        public Set<String> permisosDe(UUID usuarioId) {
            return rolesDe(usuarioId).stream().flatMap(r -> permisosDel(r).stream()).collect(Collectors.toSet());
        }

        @Override
        public Map<Rol, Set<String>> matrizPersistida() {
            return Map.of();
        }

        @Override
        public Set<UUID> usuariosConRol(Rol rol) {
            return roles.entrySet().stream().filter(e -> e.getValue().contains(rol)).map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }

        @Override
        public Map<UUID, Set<Rol>> rolesDe(Set<UUID> usuarioIds) {
            throw new UnsupportedOperationException("La gestión de roles no proyecta páginas");
        }

        @Override
        public void bloquearFilaAdministrador() {
            llamadas.add("lock catálogo");
            if (alBloquearCatalogo != null) {
                alBloquearCatalogo.run();
            }
        }

        @Override
        public boolean asignar(UUID usuarioId, Rol rol) {
            llamadas.add("asignar " + rol);
            return roles.computeIfAbsent(usuarioId, id -> EnumSet.noneOf(Rol.class)).add(rol);
        }

        @Override
        public boolean quitar(UUID usuarioId, Rol rol) {
            llamadas.add("quitar " + rol);
            return roles.getOrDefault(usuarioId, EnumSet.noneOf(Rol.class)).remove(rol);
        }
    };

    /** AutorizacionService real sobre el doble: decide con los roles «de la base». */
    private final AutorizacionPort autorizacion = new AutorizacionPort() {
        private final AutorizacionService real = new AutorizacionService(asociaciones);

        @Override
        public com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos resolver(UUID usuarioId) {
            return real.resolver(usuarioId);
        }

        @Override
        public void exigirPermiso(ActorUsuario actor, Permiso permiso) {
            llamadas.add("exigir " + permiso.codigo());
            real.exigirPermiso(actor, permiso);
        }

        @Override
        public void exigirTitularOPrivilegio(ActorUsuario actor, UUID objetivo, Permiso permiso) {
            real.exigirTitularOPrivilegio(actor, objetivo, permiso);
        }

        @Override
        public void bloquearCatalogoAdministrador() {
            real.bloquearCatalogoAdministrador();
        }

        @Override
        public Set<UUID> usuariosConRol(Rol rol) {
            return real.usuariosConRol(rol);
        }
    };

    /** Orden en que se pidieron bloquear las cuentas en la última operación. */
    private List<UUID> bloqueadas = List.of();

    private final CuentasRolesPort cuentas = new CuentasRolesPort() {
        @Override
        public Map<UUID, CuentaRoles> bloquear(List<UUID> ids) {
            llamadas.add("lock cuentas");
            bloqueadas = ids;
            Map<UUID, CuentaRoles> existentes = new HashMap<>();
            ids.forEach(id -> Optional.ofNullable(cuentasExistentes.get(id)).ifPresent(c -> existentes.put(id, c)));
            return existentes;
        }

        @Override
        public long contarActivos(Set<UUID> usuarioIds) {
            llamadas.add("contar activos");
            return usuarioIds.stream().map(cuentasExistentes::get)
                    .filter(c -> c != null && c.activa()).count();
        }
    };

    private final SesionesRolesPort sesiones = usuarioId -> llamadas.add("revocar sesiones");

    private final AuditoriaRolesPort auditoria = new AuditoriaRolesPort() {
        @Override
        public void registrarCritica(RegistroRol registro) {
            llamadas.add("auditar " + registro.accion());
            if (falloAuditoria != null) {
                throw falloAuditoria;
            }
            auditados.add(registro);
        }

        @Override
        public void registrarNoCritica(RegistroRol registro) {
            auditados.add(registro);
        }
    };

    private final EventosRolesPort outbox = evento -> {
        llamadas.add("evento");
        eventos.add(evento);
    };

    private final GestionRolesService servicio = new GestionRolesService(autorizacion, asociaciones, cuentas,
            sesiones, auditoria, outbox, Clock.fixed(AHORA, ZoneOffset.UTC));

    private static Set<String> permisosDel(Rol rol) {
        return com.marketplace.auth_service.autorizacion.domain.model.PermisosEfectivos
                .desdeCatalogo(Set.of(rol)).codigosPermisos().stream().collect(Collectors.toSet());
    }

    private UUID persona(String estado, Rol... asignados) {
        return persona(UUID.randomUUID(), estado, asignados);
    }

    private UUID persona(UUID id, String estado, Rol... asignados) {
        roles.put(id, asignados.length == 0 ? EnumSet.noneOf(Rol.class) : EnumSet.copyOf(List.of(asignados)));
        cuentasExistentes.put(id, new CuentaRoles(id, "Persona " + id, id + "@ejemplo.test", estado));
        return id;
    }

    private static ActorUsuario actor(UUID sub, String... permisosDelToken) {
        return new ActorUsuario(sub, UUID.randomUUID(), Set.of(), Set.of(permisosDelToken));
    }

    private static CambioRolCommand asignar(UUID actor, UUID objetivo, String rol) {
        return new CambioRolCommand(actor(actor), objetivo, rol, CONTEXTO);
    }

    /** El comando es el mismo; cambia la operación a la que se pasa. */
    private static CambioRolCommand revocar(UUID actor, UUID objetivo, String rol) {
        return asignar(actor, objetivo, rol);
    }

    // ── ESC-11.1 ──────────────────────────────────────────────

    @Test
    void asignaEnElOrdenDeLocksYConfirmaLosEfectos() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = persona("ACTIVO", Rol.VENDEDOR);

        RolesCambiados resultado = servicio.asignar(asignar(admin, vendedor, "GESTOR_DESPACHO"));

        assertThat(llamadas).containsExactly(
                "exigir rol.asignar", "lock catálogo", "lock cuentas", "exigir rol.asignar",
                "revocar sesiones", "asignar GESTOR_DESPACHO", "auditar ROL_ASIGNADO", "evento");
        assertThat(bloqueadas).as("actor y objetivo").containsExactlyInAnyOrder(admin, vendedor);
        assertThat(resultado.huboCambio()).isTrue();
        assertThat(resultado.roles()).containsExactly(Rol.VENDEDOR, Rol.GESTOR_DESPACHO);
        assertThat(resultado.cuenta().id()).isEqualTo(vendedor);
    }

    @Test
    void laAuditoriaLlevaActorObjetivoRolIpYFecha() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = persona("ACTIVO", Rol.VENDEDOR);

        servicio.asignar(asignar(admin, vendedor, "GESTOR_DESPACHO"));

        RegistroRol registro = auditados.get(0);
        assertThat(registro.accion()).isEqualTo(RegistroRol.Accion.ROL_ASIGNADO);
        assertThat(registro.resultado()).isEqualTo("EXITO");
        assertThat(registro.actor()).isEqualTo(admin);
        assertThat(registro.objetivo()).isEqualTo(vendedor);
        assertThat(registro.rol()).isEqualTo(Rol.GESTOR_DESPACHO);
        assertThat(registro.contexto().ip()).isEqualTo("203.0.113.7");
        assertThat(registro.fecha()).isEqualTo(AHORA);
    }

    @Test
    void elEventoLlevaLaListaCompletaResultanteNoElCambio() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID objetivo = persona("ACTIVO", Rol.VENDEDOR, Rol.CLIENTE);

        servicio.asignar(asignar(admin, objetivo, "GESTOR_COMERCIAL"));

        RolesCambiadosEvento evento = eventos.get(0);
        assertThat(evento.usuarioId()).isEqualTo(objetivo);
        assertThat(evento.roles()).containsExactly(Rol.CLIENTE, Rol.VENDEDOR, Rol.GESTOR_COMERCIAL);
        assertThat(evento.eventoId()).isNotNull();
        assertThat(evento.fecha()).isEqualTo(AHORA);
    }

    @Test
    void dosAsignacionesDistintasConservanAmbosRoles() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID objetivo = persona("ACTIVO", Rol.VENDEDOR);

        servicio.asignar(asignar(admin, objetivo, "GESTOR_DESPACHO"));
        RolesCambiados segunda = servicio.asignar(asignar(admin, objetivo, "GESTOR_COMERCIAL"));

        assertThat(segunda.roles()).containsExactly(Rol.VENDEDOR, Rol.GESTOR_DESPACHO, Rol.GESTOR_COMERCIAL);
        assertThat(eventos.get(1).roles()).containsExactly(Rol.VENDEDOR, Rol.GESTOR_DESPACHO, Rol.GESTOR_COMERCIAL);
    }

    @Test
    void unRolQueYaTeniaNoRevocaSesionesNiAuditaNiPublica() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = persona("ACTIVO", Rol.VENDEDOR);

        RolesCambiados resultado = servicio.asignar(asignar(admin, vendedor, "VENDEDOR"));

        assertThat(resultado.huboCambio()).isFalse();
        assertThat(resultado.roles()).containsExactly(Rol.VENDEDOR);
        assertThat(llamadas).containsExactly("exigir rol.asignar", "lock catálogo", "lock cuentas", "exigir rol.asignar");
    }

    // ── Orden de locks (ACUERDOS-INTERNOS §3) ─────────────────

    @Test
    void bloqueaActorYObjetivoOrdenadosComoPostgresqlNoComoUuidCompareTo() {
        // compareTo de Java pone primero 8000… (bits con signo); PostgreSQL, 1000…
        UUID alto = UUID.fromString("80000000-0000-0000-0000-000000000001");
        UUID bajo = UUID.fromString("10000000-0000-0000-0000-000000000001");
        assertThat(alto.compareTo(bajo)).isNegative();

        persona(alto, "ACTIVO", Rol.ADMIN_SISTEMA);
        persona(bajo, "ACTIVO", Rol.VENDEDOR);
        servicio.asignar(asignar(alto, bajo, "GESTOR_DESPACHO"));
        assertThat(bloqueadas).containsExactly(bajo, alto);

        roles.get(alto).remove(Rol.ADMIN_SISTEMA);
        roles.get(bajo).add(Rol.ADMIN_SISTEMA);
        servicio.asignar(asignar(bajo, alto, "GESTOR_DESPACHO"));
        assertThat(bloqueadas).as("mismo orden aunque cambien los papeles").containsExactly(bajo, alto);
    }

    @Test
    void sobreSuPropiaCuentaLaBloqueaUnaSolaVez() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);

        servicio.asignar(asignar(admin, admin, "VENDEDOR"));

        assertThat(bloqueadas).containsExactly(admin);
    }

    @Test
    void lasFamiliasSeRevocanAntesDeEscribirLaAsociacion() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = persona("ACTIVO", Rol.VENDEDOR);

        servicio.asignar(asignar(admin, vendedor, "GESTOR_DESPACHO"));

        assertThat(llamadas.indexOf("revocar sesiones")).isLessThan(llamadas.indexOf("asignar GESTOR_DESPACHO"));
        assertThat(llamadas.indexOf("lock cuentas")).isLessThan(llamadas.indexOf("revocar sesiones"));
    }

    // ── ESC-11.2 ──────────────────────────────────────────────

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"BUYER", "SUPER_ADMIN", "admin_sistema", "", " ADMIN_SISTEMA", "REPARTIDOR"})
    void unCodigoFueraDelCatalogoEsInvalidoYNoTocaNada(String codigo) {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = persona("ACTIVO", Rol.VENDEDOR);

        assertThatThrownBy(() -> servicio.asignar(asignar(admin, vendedor, codigo)))
                .isInstanceOf(RolInvalidoException.class);
        assertThat(llamadas).containsExactly("exigir rol.asignar");
        assertThat(roles.get(vendedor)).containsExactly(Rol.VENDEDOR);
    }

    // ── ESC-11.3 ──────────────────────────────────────────────

    @Test
    void sinRolAsignarSeRechazaAntesDeValidarElCodigoOBuscarLaCuenta() {
        UUID adminVentas = persona("ACTIVO", Rol.ADMIN_VENTAS);
        UUID vendedor = persona("ACTIVO", Rol.VENDEDOR);

        // Código inválido y cuenta inexistente: igual responde 403, sin revelar nada.
        assertThatThrownBy(() -> servicio.asignar(asignar(adminVentas, UUID.randomUUID(), "BUYER")))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.asignar(asignar(adminVentas, vendedor, "ADMIN_SISTEMA")))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThat(llamadas).containsOnly("exigir rol.asignar");
        assertThat(roles.get(vendedor)).containsExactly(Rol.VENDEDOR);
    }

    @Test
    void losClaimsDelTokenNoDanElPermiso() {
        UUID cliente = persona("ACTIVO", Rol.CLIENTE);
        UUID vendedor = persona("ACTIVO", Rol.VENDEDOR);
        var conClaimsFalsos = new CambioRolCommand(
                new ActorUsuario(cliente, UUID.randomUUID(), Set.of("ADMIN_SISTEMA"), Set.of("rol.asignar")),
                vendedor, "ADMIN_SISTEMA", CONTEXTO);

        assertThatThrownBy(() -> servicio.asignar(conClaimsFalsos)).isInstanceOf(AccesoDenegadoException.class);
        assertThat(roles.get(vendedor)).containsExactly(Rol.VENDEDOR);
    }

    @Test
    void siPierdeElPermisoMientrasEsperaElLockNoCambiaNada() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = persona("ACTIVO", Rol.VENDEDOR);
        // Otra transacción, que tenía el lock antes, le quitó ADMIN_SISTEMA al actor.
        alBloquearCatalogo = () -> roles.get(admin).remove(Rol.ADMIN_SISTEMA);

        assertThatThrownBy(() -> servicio.asignar(asignar(admin, vendedor, "GESTOR_DESPACHO")))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThat(llamadas).containsExactly("exigir rol.asignar", "lock catálogo", "lock cuentas", "exigir rol.asignar");
        assertThat(roles.get(vendedor)).containsExactly(Rol.VENDEDOR);
    }

    // ── 404 y fallos ──────────────────────────────────────────

    @Test
    void unaCuentaInexistenteDa404DespuesDelPermisoYSinEfectos() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID inexistente = UUID.randomUUID();

        assertThatThrownBy(() -> servicio.asignar(asignar(admin, inexistente, "VENDEDOR")))
                .isInstanceOf(UsuarioNoEncontradoException.class);
        assertThat(llamadas).containsExactly("exigir rol.asignar", "lock catálogo", "lock cuentas");
        assertThat(roles).doesNotContainKey(inexistente);
    }

    @Test
    void siLaAuditoriaCriticaFallaElErrorSubeYNoSeGuardaElEvento() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID vendedor = persona("ACTIVO", Rol.VENDEDOR);
        falloAuditoria = new GestionRolesNoDisponibleException("auditoría caída");

        assertThatThrownBy(() -> servicio.asignar(asignar(admin, vendedor, "GESTOR_DESPACHO")))
                .isInstanceOf(GestionRolesNoDisponibleException.class);
        // El rollback lo hace la transacción del decorador (probado en PostgreSQL).
        assertThat(llamadas).endsWith("auditar ROL_ASIGNADO").doesNotContain("evento");
        assertThat(eventos).isEmpty();
    }

    @Test
    void sePuedeAsignarAUnaCuentaQueNoEstaActiva() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID bloqueada = persona("BLOQUEADO", Rol.VENDEDOR);

        RolesCambiados resultado = servicio.asignar(asignar(admin, bloqueada, "GESTOR_DESPACHO"));

        assertThat(resultado.roles()).containsExactly(Rol.VENDEDOR, Rol.GESTOR_DESPACHO);
        assertThat(resultado.cuenta().estado()).isEqualTo("BLOQUEADO");
    }

    @Test
    void sinProveedoresElPermisoSeCompruebaYLuegoResponde503() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID cliente = persona("ACTIVO", Rol.CLIENTE);
        var noDisponible = new GestionRolesNoDisponible(autorizacion, List.of("CuentasRolesPort (cuentas, Eva)"));

        assertThatThrownBy(() -> noDisponible.asignar(asignar(cliente, admin, "VENDEDOR")))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> noDisponible.asignar(asignar(admin, cliente, "VENDEDOR")))
                .isInstanceOf(GestionRolesNoDisponibleException.class)
                .hasMessageContaining("CuentasRolesPort");
        assertThat(roles.get(cliente)).containsExactly(Rol.CLIENTE);
    }

    // ── Revocación (EP-05-T004) ───────────────────────────────

    @Test
    void revocaUnRolConservandoLosDemasEnElMismoOrdenDeLocks() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID objetivo = persona("ACTIVO", Rol.VENDEDOR, Rol.GESTOR_DESPACHO);

        RolesCambiados resultado = servicio.revocar(revocar(admin, objetivo, "GESTOR_DESPACHO"));

        assertThat(llamadas).containsExactly(
                "exigir rol.asignar", "lock catálogo", "lock cuentas", "exigir rol.asignar",
                "revocar sesiones", "quitar GESTOR_DESPACHO", "auditar ROL_REVOCADO", "evento");
        assertThat(bloqueadas).as("actor y objetivo").containsExactlyInAnyOrder(admin, objetivo);
        assertThat(resultado.roles()).containsExactly(Rol.VENDEDOR);
        assertThat(eventos.get(0).roles()).containsExactly(Rol.VENDEDOR);
        assertThat(auditados.get(0).accion()).isEqualTo(RegistroRol.Accion.ROL_REVOCADO);
        assertThat(auditados.get(0).rol()).isEqualTo(Rol.GESTOR_DESPACHO);
    }

    @Test
    void revocarElUltimoRolDejaLaListaVaciaYPublicaArrayVacio() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID objetivo = persona("ACTIVO", Rol.CLIENTE);

        RolesCambiados resultado = servicio.revocar(revocar(admin, objetivo, "CLIENTE"));

        assertThat(resultado.roles()).isEmpty();
        assertThat(eventos.get(0).roles()).isEmpty();
    }

    @Test
    void revocarUnRolQueNoTieneNoTieneEfectos() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID objetivo = persona("ACTIVO", Rol.VENDEDOR);

        RolesCambiados resultado = servicio.revocar(revocar(admin, objetivo, "GESTOR_COMERCIAL"));

        assertThat(resultado.huboCambio()).isFalse();
        assertThat(llamadas).containsExactly("exigir rol.asignar", "lock catálogo", "lock cuentas", "exigir rol.asignar");
    }

    @Test
    void elUnicoAdministradorNoPuedeQuitarseSuRol() {
        UUID unico = persona("ACTIVO", Rol.ADMIN_SISTEMA);

        assertThatThrownBy(() -> servicio.revocar(revocar(unico, unico, "ADMIN_SISTEMA")))
                .isInstanceOf(AdministradorProtegidoException.class)
                .extracting("motivo").isEqualTo(AdministradorProtegidoException.Motivo.ULTIMO_ADMINISTRADOR);
        assertThat(roles.get(unico)).containsExactly(Rol.ADMIN_SISTEMA);
        assertThat(llamadas).doesNotContain("revocar sesiones").noneMatch(l -> l.startsWith("quitar"));
    }

    @Test
    void unAdministradorNoSeQuitaSuPropioRolAunqueHayaOtros() {
        UUID yo = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        persona("ACTIVO", Rol.ADMIN_SISTEMA);

        assertThatThrownBy(() -> servicio.revocar(revocar(yo, yo, "ADMIN_SISTEMA")))
                .extracting("motivo").isEqualTo(AdministradorProtegidoException.Motivo.PROPIO);
        assertThat(roles.get(yo)).containsExactly(Rol.ADMIN_SISTEMA);
    }

    @Test
    void unAdministradorSiPuedeQuitarseOtroRolPropio() {
        UUID yo = persona("ACTIVO", Rol.ADMIN_SISTEMA, Rol.VENDEDOR);

        RolesCambiados resultado = servicio.revocar(revocar(yo, yo, "VENDEDOR"));

        assertThat(resultado.roles()).containsExactly(Rol.ADMIN_SISTEMA);
    }

    @Test
    void conOtroAdministradorActivoSePuedeRevocarAUnAdministrador() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID otro = persona("ACTIVO", Rol.ADMIN_SISTEMA);

        RolesCambiados resultado = servicio.revocar(revocar(admin, otro, "ADMIN_SISTEMA"));

        assertThat(resultado.roles()).isEmpty();
        assertThat(llamadas).contains("contar activos");
    }

    @Test
    void losAdministradoresBloqueadosOInactivosNoCuentanComoActivos() {
        UUID actor = persona("BLOQUEADO", Rol.ADMIN_SISTEMA);
        UUID ultimoActivo = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        persona("INACTIVO", Rol.ADMIN_SISTEMA);

        // El actor conserva el permiso en la base (su acceso sigue vigente), pero su cuenta
        // está bloqueada: quitarle el rol al único activo dejaría el sistema sin administrador.
        assertThatThrownBy(() -> servicio.revocar(revocar(actor, ultimoActivo, "ADMIN_SISTEMA")))
                .extracting("motivo").isEqualTo(AdministradorProtegidoException.Motivo.ULTIMO_ADMINISTRADOR);
        assertThat(roles.get(ultimoActivo)).containsExactly(Rol.ADMIN_SISTEMA);
    }

    @Test
    void revocarAUnAdministradorNoActivoNoReduceLosActivosYSePermite() {
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);
        UUID bloqueado = persona("BLOQUEADO", Rol.ADMIN_SISTEMA);

        RolesCambiados resultado = servicio.revocar(revocar(admin, bloqueado, "ADMIN_SISTEMA"));

        assertThat(resultado.roles()).isEmpty();
        assertThat(llamadas).doesNotContain("contar activos");
    }

    @Test
    void laRevocacionTambienAutorizaPrimeroYValidaDespues() {
        UUID cliente = persona("ACTIVO", Rol.CLIENTE);
        UUID admin = persona("ACTIVO", Rol.ADMIN_SISTEMA);

        assertThatThrownBy(() -> servicio.revocar(revocar(cliente, admin, "BUYER")))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.revocar(revocar(admin, cliente, "BUYER")))
                .isInstanceOf(RolInvalidoException.class);
        assertThatThrownBy(() -> servicio.revocar(revocar(admin, UUID.randomUUID(), "CLIENTE")))
                .isInstanceOf(UsuarioNoEncontradoException.class);
        assertThat(roles.get(admin)).containsExactly(Rol.ADMIN_SISTEMA);
    }
}
