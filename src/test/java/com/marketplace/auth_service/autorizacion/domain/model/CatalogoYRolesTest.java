package com.marketplace.auth_service.autorizacion.domain.model;

import com.marketplace.auth_service.autorizacion.domain.exception.RolInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Catálogo cerrado de roles y permisos (RF-11.1, RF-11.9), sin base de datos. */
class CatalogoYRolesTest {

    @Test
    void hayExactamenteSeisRolesConLosCodigosDelContrato() {
        assertThat(Arrays.stream(Rol.values()).map(Rol::name)).containsExactly(
                "CLIENTE", "VENDEDOR", "ADMIN_VENTAS", "GESTOR_DESPACHO", "GESTOR_COMERCIAL", "ADMIN_SISTEMA");
    }

    @Test
    void cadaCodigoValidoSeConvierteEnSuRol() {
        for (Rol rol : Rol.values()) {
            assertThat(Rol.desdeCodigo(rol.name())).isSameAs(rol);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUYER", "SELLER", "SUPER_ADMIN", "SYSTEM_ADMIN", "admin_sistema", "REPARTIDOR", " CLIENTE"})
    @NullAndEmptySource
    void esc11_2_codigosFueraDelCatalogoSeRechazan(String codigo) {
        assertThatThrownBy(() -> Rol.desdeCodigo(codigo)).isInstanceOf(RolInvalidoException.class);
    }

    @Test
    void losRolesDeGestionSonLosCuatroDelGlosario() {
        assertThat(Arrays.stream(Rol.values()).filter(Rol::esDeGestion)).containsExactlyInAnyOrder(
                Rol.ADMIN_VENTAS, Rol.GESTOR_DESPACHO, Rol.GESTOR_COMERCIAL, Rol.ADMIN_SISTEMA);
    }

    @Test
    void lasEtiquetasSonLasDeFront00() {
        assertThat(Rol.CLIENTE.etiqueta()).isEqualTo("Cliente");
        assertThat(Rol.ADMIN_SISTEMA.etiqueta()).isEqualTo("Administrador del sistema");
    }

    @Test
    void hayDiezPermisosTodosRecursoPuntoAccion() {
        assertThat(Permiso.values()).hasSize(10);
        assertThat(Arrays.stream(Permiso.values()).map(Permiso::codigo))
                .allSatisfy(c -> assertThat(c).matches("^[a-z_]+\\.[a-z_]+$"));
    }

    @Test
    void unScopeConDosPuntosNoEsUnPermiso() {
        assertThat(Permiso.desdeCodigo("roles:leer")).isEmpty();
        assertThat(Permiso.desdeCodigo("pedido.crear")).isEmpty();
        assertThat(Permiso.desdeCodigo("rol.asignar")).contains(Permiso.ROL_ASIGNAR);
    }

    @Test
    void rf11_9_adminTieneOchoVendedorDosYElRestoNinguno() {
        assertThat(CatalogoAutorizacion.permisosDe(Rol.ADMIN_SISTEMA)).hasSize(8)
                .doesNotContain(Permiso.CLIENTE_REGISTRAR, Permiso.CLIENTE_BUSCAR);
        assertThat(CatalogoAutorizacion.permisosDe(Rol.VENDEDOR))
                .containsExactlyInAnyOrder(Permiso.CLIENTE_REGISTRAR, Permiso.CLIENTE_BUSCAR);
        for (Rol rol : List.of(Rol.CLIENTE, Rol.ADMIN_VENTAS, Rol.GESTOR_DESPACHO, Rol.GESTOR_COMERCIAL)) {
            assertThat(CatalogoAutorizacion.permisosDe(rol)).isEmpty();
        }
    }

    @Test
    void cadaPermisoLoConcedeExactamenteUnRol() {
        Set<Permiso> vistos = EnumSet.noneOf(Permiso.class);
        CatalogoAutorizacion.matriz().values().forEach(permisos ->
                permisos.forEach(p -> assertThat(vistos.add(p)).as("%s repetido", p).isTrue()));
        assertThat(vistos).containsExactlyInAnyOrder(Permiso.values());
    }

    @Test
    void laMatrizNoSePuedeModificar() {
        assertThatThrownBy(() -> CatalogoAutorizacion.permisosDe(Rol.CLIENTE).add(Permiso.USUARIO_VER))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
