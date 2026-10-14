package com.marketplace.auth_service.autorizacion.application.service;

import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.application.port.out.AsociacionesRolPort;
import com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException;
import com.marketplace.auth_service.autorizacion.domain.model.PermisosEfectivos;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos de uso de autorización con un doble del puerto de persistencia: resolución,
 * decisiones de permiso y titularidad (RF-11.4, RF-11.10, ESC-11.3) y guard.
 */
class AutorizacionServiceTest {

    private final Map<UUID, Set<Rol>> roles = new HashMap<>();
    private final Map<UUID, Set<String>> permisos = new HashMap<>();
    private int consultasDePermisos;
    private int bloqueos;

    private final AsociacionesRolPort doble = new AsociacionesRolPort() {
        @Override
        public Set<Rol> rolesDe(UUID usuarioId) {
            return roles.getOrDefault(usuarioId, Set.of());
        }

        @Override
        public Set<String> permisosDe(UUID usuarioId) {
            consultasDePermisos++;
            return permisos.getOrDefault(usuarioId, Set.of());
        }

        @Override
        public Map<Rol, Set<String>> matrizPersistida() {
            return Map.of();
        }

        @Override
        public Set<UUID> usuariosConRol(Rol rol) {
            return roles.entrySet().stream().filter(e -> e.getValue().contains(rol))
                    .map(Map.Entry::getKey).collect(Collectors.toSet());
        }

        @Override
        public void bloquearFilaAdministrador() {
            bloqueos++;
        }
    };

    /** Persona con esos roles y los permisos que les da el catálogo, como lo haría la base. */
    private UUID persona(Rol... asignados) {
        UUID id = UUID.randomUUID();
        roles.put(id, Set.of(asignados));
        permisos.put(id, PermisosEfectivos.desdeCatalogo(Set.of(asignados)).codigosPermisos().stream()
                .collect(Collectors.toSet()));
        return id;
    }

    /** Actor con los claims que diga su token, que pueden no coincidir con la base. */
    private static ActorUsuario actor(UUID sub, String... permisosDelToken) {
        return new ActorUsuario(sub, UUID.randomUUID(), Set.of(), Set.of(permisosDelToken));
    }

    private final AutorizacionService servicio = new AutorizacionService(doble);

    @Test
    void convierteLoQueDiceLaBaseEnRolesYPermisos() {
        UUID id = UUID.randomUUID();
        roles.put(id, Set.of(Rol.ADMIN_SISTEMA));
        permisos.put(id, Set.of("rol.asignar", "usuario.ver"));

        PermisosResueltos efectivos = servicio.resolver(id);

        assertThat(efectivos.codigosRoles()).containsExactly("ADMIN_SISTEMA");
        assertThat(efectivos.codigosPermisos()).containsExactly("rol.asignar", "usuario.ver");
    }

    @Test
    void sinRolesNoConsultaPermisos() {
        PermisosResueltos efectivos = servicio.resolver(UUID.randomUUID());

        assertThat(efectivos).isEqualTo(PermisosResueltos.ninguno());
        assertThat(consultasDePermisos).isZero();
    }

    @Test
    void unPermisoFueraDelCatalogoDetieneLaResolucion() {
        UUID id = UUID.randomUUID();
        roles.put(id, Set.of(Rol.CLIENTE));
        permisos.put(id, Set.of("pedido.crear"));

        assertThatThrownBy(() -> servicio.resolver(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pedido.crear");
    }

    @Test
    void elUsuarioEsObligatorio() {
        assertThatThrownBy(() -> servicio.resolver(null)).isInstanceOf(NullPointerException.class);
    }

    // ── exigirPermiso (EP-05-T002) ────────────────────────────

    @Test
    void unAdministradorPuedeAsignarRoles() {
        UUID admin = persona(Rol.ADMIN_SISTEMA);

        servicio.exigirPermiso(actor(admin), Permiso.ROL_ASIGNAR);
    }

    @Test
    void esc11_3_adminVentasYClienteNoTienenRolAsignar() {
        for (Rol rol : new Rol[]{Rol.ADMIN_VENTAS, Rol.CLIENTE, Rol.VENDEDOR}) {
            UUID id = persona(rol);
            assertThatThrownBy(() -> servicio.exigirPermiso(actor(id), Permiso.ROL_ASIGNAR))
                    .isInstanceOf(AccesoDenegadoException.class)
                    .hasMessage("Permisos insuficientes");
        }
    }

    @Test
    void unPermisoRetiradoDespuesDeEmitirElTokenYaNoSirve() {
        UUID exAdmin = persona(Rol.CLIENTE);

        // Su token todavía dice rol.asignar, pero la base ya no.
        assertThatThrownBy(() -> servicio.exigirPermiso(actor(exAdmin, "rol.asignar"), Permiso.ROL_ASIGNAR))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void unPermisoConcedidoDespuesDeEmitirElTokenYaSirve() {
        UUID nuevoAdmin = persona(Rol.ADMIN_SISTEMA);

        servicio.exigirPermiso(actor(nuevoAdmin), Permiso.ROL_ASIGNAR);
    }

    @Test
    void laDenegacionConservaActorYPermisoParaAuditar() {
        UUID id = persona(Rol.CLIENTE);

        assertThatThrownBy(() -> servicio.exigirPermiso(actor(id), Permiso.USUARIO_VER))
                .isInstanceOfSatisfying(AccesoDenegadoException.class, e -> {
                    assertThat(e.actor()).isEqualTo(id);
                    assertThat(e.permiso()).isEqualTo(Permiso.USUARIO_VER);
                });
    }

    // ── exigirTitularOPrivilegio (RF-11.10) ───────────────────

    @Test
    void elTitularActuaSobreSuCuentaSinPermiso() {
        UUID cliente = persona(Rol.CLIENTE);

        servicio.exigirTitularOPrivilegio(actor(cliente), cliente, Permiso.USUARIO_VER);
    }

    @Test
    void sobreOtraCuentaHaceFaltaElPermiso() {
        UUID cliente = persona(Rol.CLIENTE);
        UUID otro = persona(Rol.CLIENTE);
        UUID admin = persona(Rol.ADMIN_SISTEMA);

        assertThatThrownBy(() -> servicio.exigirTitularOPrivilegio(actor(cliente), otro, Permiso.USUARIO_VER))
                .isInstanceOf(AccesoDenegadoException.class);
        servicio.exigirTitularOPrivilegio(actor(admin), otro, Permiso.USUARIO_VER);
    }

    @Test
    void laTitularidadNoAmpliaLosCamposPrivilegiados() {
        UUID vendedor = persona(Rol.VENDEDOR);

        // Documento y atributos de rol: exigirPermiso, incluso sobre la cuenta propia.
        assertThatThrownBy(() -> servicio.exigirPermiso(actor(vendedor), Permiso.ATRIBUTOS_EDITAR))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    // ── Guard y consultas ─────────────────────────────────────

    @Test
    void elGuardDelegaElBloqueoEnLaPersistencia() {
        servicio.bloquearCatalogoAdministrador();

        assertThat(bloqueos).isEqualTo(1);
    }

    @Test
    void devuelveQuienesTienenUnRol() {
        UUID a = persona(Rol.ADMIN_SISTEMA);
        UUID b = persona(Rol.ADMIN_SISTEMA, Rol.VENDEDOR);
        persona(Rol.CLIENTE);

        assertThat(servicio.usuariosConRol(Rol.ADMIN_SISTEMA)).containsExactlyInAnyOrder(a, b);
    }
}
