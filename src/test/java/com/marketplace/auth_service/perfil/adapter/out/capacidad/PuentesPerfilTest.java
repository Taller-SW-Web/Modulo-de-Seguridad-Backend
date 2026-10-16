package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.CampoEditable;
import com.marketplace.auth_service.perfil.application.dto.CuentaBasica;
import com.marketplace.auth_service.perfil.domain.model.CuentaPerfilNoEncontradaException;
import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.port.in.CambioBasicos;
import com.marketplace.auth_service.usuario.application.port.in.ContadoresProteccion;
import com.marketplace.auth_service.usuario.application.port.in.CuentaEscrituraInterna;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Los puentes compilan contra los contratos reales de Eva y Sergio: un cambio incompatible rompe esta prueba. */
class PuentesPerfilTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static CuentaSnapshot snapshot(boolean mfaVoluntaria, boolean celularVerificado) {
        return new CuentaSnapshot(A, "Ana", "Perez", "ana@x.pe", "+51987654321", "ACTIVO", "BLOQUEADO",
                true, celularVerificado, mfaVoluntaria, 2, 3);
    }

    private static final class CuentasDoble implements CuentasPort {
        Optional<CuentaSnapshot> cuenta = Optional.empty();
        final List<UUID> bloqueos = new ArrayList<>();

        public Optional<CuentaSnapshot> buscarPorCorreoNormalizado(String c) { throw new UnsupportedOperationException(); }
        public Optional<CuentaSnapshot> leer(UUID id) { return cuenta; }
        public CuentaSnapshot bloquear(UUID id) { bloqueos.add(id); return cuenta.orElseThrow(); }
        public long contarActivos(Set<UUID> ids) { throw new UnsupportedOperationException(); }
        public PaginaResultado<CuentaSnapshot> consultar(FiltroCuenta f, Paginacion p) { throw new UnsupportedOperationException(); }
    }

    private static final class EscrituraDoble implements CuentaEscrituraInterna {
        CambioBasicos recibido;

        public CuentaSnapshot actualizarBasicos(UUID id, CambioBasicos cambio) { recibido = cambio; return null; }
        public void guardarContadores(UUID id, ContadoresProteccion c) { throw new UnsupportedOperationException(); }
        public void guardarMfaVoluntario(UUID id, boolean h) { throw new UnsupportedOperationException(); }
    }

    private static final class AutorizacionDoble implements AutorizacionPort {
        PermisosResueltos permisos = PermisosResueltos.ninguno();
        int resoluciones;

        public PermisosResueltos resolver(UUID id) { resoluciones++; return permisos; }
        public void exigirPermiso(ActorUsuario a, Permiso p) { throw new UnsupportedOperationException(); }
        public void exigirTitularOPrivilegio(ActorUsuario a, UUID o, Permiso p) { throw new UnsupportedOperationException(); }
        public void bloquearCatalogoAdministrador() { throw new UnsupportedOperationException(); }
        public Set<UUID> usuariosConRol(Rol rol) { throw new UnsupportedOperationException(); }
    }

    private static ActorUsuario actor(UUID sub) {
        return new ActorUsuario(sub, UUID.randomUUID(), Set.of("ADMIN_SISTEMA"), Set.of("usuario.ver"));
    }

    @Test
    void cuentasTomaEstadoEfectivoYNoLlevaContadoresNiBanderaMfa() {
        CuentasDoble cuentas = new CuentasDoble();
        cuentas.cuenta = Optional.of(snapshot(true, false));

        CuentaBasica b = new CuentasPerfilAdapter(cuentas).leer(A).orElseThrow();

        assertThat(b.estado()).isEqualTo("BLOQUEADO");
        assertThat(b.nombreCompleto()).isEqualTo("Ana Perez");
        assertThat(b.celularVerificado()).isFalse();
        assertThat(new CuentasPerfilAdapter(new CuentasDoble()).leer(A).isPresent()).isFalse();
    }

    @Test
    void escrituraBloqueaYTraduceOmitidoYPresenteALosCampoBasicoDeEva() {
        CuentasDoble cuentas = new CuentasDoble();
        EscrituraDoble escritura = new EscrituraDoble();
        EscrituraCuentaPerfilAdapter puente = new EscrituraCuentaPerfilAdapter(cuentas, escritura);
        cuentas.cuenta = Optional.of(snapshot(false, true));

        puente.bloquear(A);
        puente.actualizarBasicos(A, new CambioAtributos(
                CampoEditable.presente("Eva"), CampoEditable.omitido(), CampoEditable.presente("+51900000000")));

        assertThat(cuentas.bloqueos).containsExactly(A);
        assertThat(escritura.recibido.nombres().presente()).isTrue();
        assertThat(escritura.recibido.nombres().valor()).isEqualTo("Eva");
        assertThat(escritura.recibido.apellidos().presente()).isFalse();
        assertThat(escritura.recibido.celular().valor()).isEqualTo("+51900000000");
    }

    @Test
    void rolesSalenComoCodigosDeLaBase() {
        AutorizacionDoble autorizacion = new AutorizacionDoble();
        autorizacion.permisos = new PermisosResueltos(Set.of(Rol.CLIENTE, Rol.VENDEDOR), Set.of());

        assertThat(new RolesPerfilAdapter(autorizacion).rolesDe(A)).isEqualTo(Set.of("CLIENTE", "VENDEDOR"));
        assertThat(new RolesPerfilAdapter(new AutorizacionDoble()).rolesDe(A)).isEqualTo(Set.of());
    }

    @Test
    void titularVePerfilSinConsultarPermisos() {
        AutorizacionDoble autorizacion = new AutorizacionDoble();

        assertThat(new AutorizacionPerfilAdapter(autorizacion).puedeVerPerfil(actor(A), A)).isTrue();
        assertThat(autorizacion.resoluciones).isEqualTo(0);
    }

    @Test
    void otroSoloConUsuarioVerActualDeLaBaseNoDelToken() {
        AutorizacionDoble autorizacion = new AutorizacionDoble();
        AutorizacionPerfilAdapter puente = new AutorizacionPerfilAdapter(autorizacion);

        // El token del actor dice usuario.ver, pero la base ya no lo tiene.
        assertThat(puente.puedeVerPerfil(actor(A), B)).isFalse();

        autorizacion.permisos = new PermisosResueltos(Set.of(Rol.ADMIN_SISTEMA), Set.of(Permiso.USUARIO_VER));
        assertThat(puente.puedeVerPerfil(actor(A), B)).isTrue();
    }

    @Test
    void mfaEfectivoEsBanderaVoluntariaOCualquierRolDeGestion() {
        CuentasDoble cuentas = new CuentasDoble();
        AutorizacionDoble autorizacion = new AutorizacionDoble();
        MfaEfectivoPerfilAdapter puente = new MfaEfectivoPerfilAdapter(cuentas, autorizacion);

        cuentas.cuenta = Optional.of(snapshot(false, true));
        assertThat(puente.efectivo(A)).isFalse();

        autorizacion.permisos = new PermisosResueltos(Set.of(Rol.CLIENTE, Rol.VENDEDOR), Set.of());
        assertThat(puente.efectivo(A)).isFalse();

        autorizacion.permisos = new PermisosResueltos(Set.of(Rol.GESTOR_DESPACHO), Set.of());
        assertThat(puente.efectivo(A)).isTrue();

        cuentas.cuenta = Optional.of(snapshot(true, true));
        autorizacion.permisos = PermisosResueltos.ninguno();
        assertThat(puente.efectivo(A)).isTrue();
    }

    @Test
    void mfaEfectivoSinCuentaNoFingeFalse() {
        MfaEfectivoPerfilAdapter puente = new MfaEfectivoPerfilAdapter(new CuentasDoble(), new AutorizacionDoble());
        assertThatThrownBy(() -> puente.efectivo(A)).isInstanceOf(CuentaPerfilNoEncontradaException.class);
    }
}
