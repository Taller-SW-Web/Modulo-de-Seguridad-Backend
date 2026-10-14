package com.marketplace.auth_service.autorizacion.domain.model;

import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Unión sin duplicados y forma de los claims (RF-11.4, ESC-11.4, ESC-11.7). */
class PermisosEfectivosTest {

    @Test
    void esc11_4_adminYVendedorDanLosDiezPermisos() {
        PermisosEfectivos efectivos = PermisosEfectivos.desdeCatalogo(List.of(Rol.VENDEDOR, Rol.ADMIN_SISTEMA));

        assertThat(efectivos.codigosRoles()).containsExactly("ADMIN_SISTEMA", "VENDEDOR");
        assertThat(efectivos.codigosPermisos()).hasSize(10).doesNotHaveDuplicates().isSorted();
    }

    @Test
    void sinRolesSonListasVaciasNoNulas() {
        PermisosEfectivos ninguno = PermisosEfectivos.ninguno();

        assertThat(ninguno.codigosRoles()).isNotNull().isEmpty();
        assertThat(ninguno.codigosPermisos()).isNotNull().isEmpty();
    }

    @Test
    void lasListasSalenSiempreEnElMismoOrden() {
        PermisosEfectivos a = PermisosEfectivos.desdeCatalogo(List.of(Rol.ADMIN_SISTEMA, Rol.CLIENTE));
        PermisosEfectivos b = PermisosEfectivos.desdeCatalogo(List.of(Rol.CLIENTE, Rol.ADMIN_SISTEMA));

        assertThat(a.codigosRoles()).isEqualTo(b.codigosRoles()).containsExactly("ADMIN_SISTEMA", "CLIENTE");
        assertThat(a.codigosPermisos()).isEqualTo(b.codigosPermisos());
    }

    @Test
    void consultaRolesYPermisos() {
        PermisosEfectivos efectivos = new PermisosEfectivos(Set.of(Rol.ADMIN_SISTEMA), Set.of(Permiso.ROL_ASIGNAR));

        assertThat(efectivos.tiene(Permiso.ROL_ASIGNAR)).isTrue();
        assertThat(efectivos.tiene(Permiso.USUARIO_VER)).isFalse();
        assertThat(efectivos.tiene(Rol.ADMIN_SISTEMA)).isTrue();
        assertThat(efectivos.tiene(Rol.VENDEDOR)).isFalse();
    }
}
