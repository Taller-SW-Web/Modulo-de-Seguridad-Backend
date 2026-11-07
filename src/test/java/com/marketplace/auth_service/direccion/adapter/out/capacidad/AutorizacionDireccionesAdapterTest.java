package com.marketplace.auth_service.direccion.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Titular o privilegio vigente: se decide con {@code resolver} (base), nunca con los claims del token. */
class AutorizacionDireccionesAdapterTest {
    private final UUID persona = UUID.randomUUID();
    private final UUID cliente = UUID.randomUUID();
    private int consultas;

    private AutorizacionDireccionesAdapter con(PermisosResueltos vigentes) {
        return new AutorizacionDireccionesAdapter(new AutorizacionPort() {
            @Override public PermisosResueltos resolver(UUID usuarioId) { consultas++; return vigentes; }
            @Override public void exigirPermiso(ActorUsuario a, Permiso p) { throw new UnsupportedOperationException(); }
            @Override public void exigirTitularOPrivilegio(ActorUsuario a, UUID o, Permiso p) { throw new UnsupportedOperationException(); }
            @Override public void bloquearCatalogoAdministrador() { throw new UnsupportedOperationException(); }
            @Override public Set<UUID> usuariosConRol(Rol rol) { throw new UnsupportedOperationException(); }
        });
    }

    private ActorUsuario actor(Set<String> roles, Set<String> permisos) {
        return new ActorUsuario(persona, UUID.randomUUID(), roles, permisos);
    }

    @Test
    void elTitularPasaSinConsultarPermisos() {
        var adaptador = con(PermisosResueltos.ninguno());
        var titular = new ActorUsuario(cliente, UUID.randomUUID(), Set.of(), Set.of());

        assertThat(adaptador.puedeVer(titular, cliente)).isTrue();
        assertThat(adaptador.puedeAgregar(titular, cliente)).isTrue();
        assertThat(consultas).isZero();
    }

    @Test
    void usuarioVerVigenteLeeAjenoPeroNoAnade() {
        var adaptador = con(new PermisosResueltos(Set.of(Rol.ADMIN_VENTAS), Set.of(Permiso.USUARIO_VER)));

        assertThat(adaptador.puedeVer(actor(Set.of(), Set.of()), cliente)).isTrue();
        assertThat(adaptador.puedeAgregar(actor(Set.of(), Set.of()), cliente)).isFalse();
    }

    @Test
    void adminSistemaVigenteAnadeEnCuentaAjena() {
        var adaptador = con(new PermisosResueltos(Set.of(Rol.ADMIN_SISTEMA), Set.of()));

        assertThat(adaptador.puedeAgregar(actor(Set.of(), Set.of()), cliente)).isTrue();
    }

    @Test
    void losClaimsDelTokenNoAutorizanSiLaBaseYaNoLosTiene() {
        var adaptador = con(PermisosResueltos.ninguno());
        var conClaims = actor(Set.of("ADMIN_SISTEMA"), Set.of("usuario.ver"));

        assertThat(adaptador.puedeVer(conClaims, cliente)).isFalse();
        assertThat(adaptador.puedeAgregar(conClaims, cliente)).isFalse();
        assertThat(consultas).isEqualTo(2);
    }
}
