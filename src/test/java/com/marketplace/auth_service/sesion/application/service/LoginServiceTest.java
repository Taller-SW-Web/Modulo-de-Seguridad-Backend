package com.marketplace.auth_service.sesion.application.service;

import com.marketplace.auth_service.sesion.application.dto.CredencialesLogin;
import com.marketplace.auth_service.sesion.application.dto.CuentaLogin;
import com.marketplace.auth_service.sesion.application.dto.DesafioCreado;
import com.marketplace.auth_service.sesion.application.dto.DesafioMfaRespuesta;
import com.marketplace.auth_service.sesion.application.dto.EstadoCuentaLogin;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.PrevisionCredencial;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.sesion.application.dto.RevisionCredencial;
import com.marketplace.auth_service.sesion.application.dto.RolesSesion;
import com.marketplace.auth_service.sesion.application.dto.SecretoEfimero;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.dto.UsuarioAutenticado;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.dto.AccesoVerificado;
import com.marketplace.auth_service.sesion.application.dto.CierreResultado;
import com.marketplace.auth_service.sesion.application.dto.ContextoSesion;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.SecretoRefresco;
import com.marketplace.auth_service.sesion.application.dto.RenovacionResultado;
import com.marketplace.auth_service.sesion.application.dto.CierreResultado;
import com.marketplace.auth_service.sesion.application.port.out.AutorizacionSesionPort;
import com.marketplace.auth_service.sesion.application.port.out.CredencialesLoginPort;
import com.marketplace.auth_service.sesion.application.port.out.CuentasLoginPort;
import com.marketplace.auth_service.sesion.application.port.out.MfaLoginPort;
import com.marketplace.auth_service.sesion.application.port.out.ProteccionLoginPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coordinador con puertos dobles, sin Spring ni DB (EP-02-T006): todas las
 * ramas con la misma respuesta contractual y sin efectos indebidos.
 */
class LoginServiceTest {
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private CuentasDoble cuentas;
    private CredencialesDoble credenciales;
    private ProteccionDoble proteccion;
    private MfaDoble mfa;
    private AutorizacionDoble autorizacion;
    private SesionesDoble sesiones;
    private LoginService servicio;
    private UUID usuario;

    @BeforeEach
    void base() {
        cuentas = new CuentasDoble();
        credenciales = new CredencialesDoble();
        proteccion = new ProteccionDoble();
        mfa = new MfaDoble();
        autorizacion = new AutorizacionDoble();
        sesiones = new SesionesDoble();
        servicio = new LoginService(cuentas, credenciales, proteccion, mfa, autorizacion, sesiones, RELOJ);
        usuario = UUID.randomUUID();
        cuentas.cuenta = new CuentaLogin(usuario, "Cliente Prueba", "c@ejemplo.test",
                EstadoCuentaLogin.ACTIVA, false);
        credenciales.coincide = true;
        autorizacion.roles = new RolesSesion(Set.of(Rol.CLIENTE), List.of());
    }

    private ContextoPeticion contexto() {
        return new ContextoPeticion("127.0.0.1", "prueba", UUID.randomUUID());
    }

    private CredencialesLogin comando() {
        return new CredencialesLogin("  C@Ejemplo.TEST ", new SecretoEfimero("secreta"));
    }

    private SesionIniciada sesionAbierta() {
        return sesiones.ultimaAbierta;
    }

    @Test
    void exitoClienteSinMfaAbreFamiliaYResetea() {
        var resultado = servicio.ingresar(comando(), contexto());

        assertThat(resultado).isInstanceOf(ResultadoLogin.Exito.class);
        assertThat(sesionAbierta().usuario().id()).isEqualTo(usuario);
        assertThat(cuentas.orden).containsExactly("buscar", "bloquear");
        assertThat(proteccion.completados).containsExactly(usuario);
        assertThat(proteccion.fallos).isEmpty();
        assertThat(credenciales.preverificaciones).hasSize(1);
    }

    @Test
    void contrasenaIncorrectaEnActivaRegistraFalloYRechaza() {
        credenciales.coincide = false;

        assertThat(servicio.ingresar(comando(), contexto()))
                .isInstanceOf(ResultadoLogin.Rechazado.class);
        assertThat(proteccion.fallos).hasSize(1);
        assertThat(proteccion.fallos.get(0).secreto().valor()).isEqualTo("secreta");
        assertThat(sesiones.ultimaAbierta).isNull();
        assertThat(proteccion.completados).isEmpty();
    }

    @Test
    void correoInexistenteComparaIgualYSinEfectos() {
        cuentas.cuenta = null;

        assertThat(servicio.ingresar(comando(), contexto()))
                .isInstanceOf(ResultadoLogin.Rechazado.class);
        assertThat(credenciales.preverificaciones).hasSize(1);
        assertThat(credenciales.preverificaciones.get(0).isEmpty()).isTrue();
        assertThat(cuentas.orden).containsExactly("buscar");
        assertThat(proteccion.fallos).isEmpty();
        assertThat(sesiones.ultimaAbierta).isNull();
    }

    @Test
    void bloqueadaInactivaYPendienteRechazanSinRegistrarNiAbrir() {
        for (EstadoCuentaLogin estado : List.of(EstadoCuentaLogin.BLOQUEADA, EstadoCuentaLogin.INACTIVA,
                EstadoCuentaLogin.PENDIENTE_VERIFICACION)) {
            cuentas.cuenta = new CuentaLogin(usuario, "Cliente", "c@ejemplo.test", estado, false);
            proteccion.fallos.clear();
            sesiones.ultimaAbierta = null;

            assertThat(servicio.ingresar(comando(), contexto())).as("%s", estado)
                    .isInstanceOf(ResultadoLogin.Rechazado.class);
            assertThat(proteccion.fallos).as("%s", estado).isEmpty();
            assertThat(sesiones.ultimaAbierta).as("%s", estado).isNull();
        }
    }

    @Test
    void contrasenaIncorrectaEnBloqueadaNoRegistra() {
        cuentas.cuenta = new CuentaLogin(usuario, "Cliente", "c@ejemplo.test",
                EstadoCuentaLogin.BLOQUEADA, false);
        credenciales.coincide = false;

        assertThat(servicio.ingresar(comando(), contexto()))
                .isInstanceOf(ResultadoLogin.Rechazado.class);
        assertThat(proteccion.fallos).isEmpty();
    }

    @Test
    void mfaEfectivoDevuelveDesafioSinFamiliaNiReset() {
        mfa.efectivo = true;
        mfa.desafio = new DesafioCreado("challenge-1", "EMAIL", 300);

        var resultado = servicio.ingresar(comando(), contexto());

        assertThat(resultado).isEqualTo(new ResultadoLogin.Desafio(
                new DesafioMfaRespuesta(true, "challenge-1", "EMAIL", 300)));
        assertThat(mfa.desafiosCreados).hasSize(1);
        assertThat(sesiones.ultimaAbierta).isNull();
        assertThat(proteccion.completados).isEmpty();
    }

    @Test
    void gestionConBanderaFalseIgualExigeMfa() {
        autorizacion.roles = new RolesSesion(Set.of(Rol.ADMIN_SISTEMA), List.of("usuario.ver"));
        mfa.porRoles = true;

        assertThat(servicio.ingresar(comando(), contexto()))
                .isInstanceOf(ResultadoLogin.Desafio.class);
        assertThat(sesiones.ultimaAbierta).isNull();
    }

    @Test
    void caducadaResponde403SinAbrir() {
        autorizacion.roles = new RolesSesion(Set.of(Rol.ADMIN_SISTEMA), List.of());
        credenciales.caducada = true;

        assertThat(servicio.ingresar(comando(), contexto()))
                .isInstanceOf(ResultadoLogin.Caducada.class);
        assertThat(sesiones.ultimaAbierta).isNull();
    }

    @Test
    void revisionCambiadaSeRepiteUnaVezSinEfectos() {
        credenciales.confirmaciones.add(false);
        credenciales.coincideSegunda = true;

        assertThat(servicio.ingresar(comando(), contexto()))
                .isInstanceOf(ResultadoLogin.Exito.class);
        assertThat(credenciales.preverificaciones).hasSize(2);
        assertThat(proteccion.fallos).isEmpty();
    }

    @Test
    void revisionCambiadaYDosFallosRegistraYRechaza() {
        credenciales.confirmaciones.add(false);
        credenciales.coincide = false;
        credenciales.coincideSegunda = false;

        assertThat(servicio.ingresar(comando(), contexto()))
                .isInstanceOf(ResultadoLogin.Rechazado.class);
        assertThat(credenciales.preverificaciones).hasSize(2);
        assertThat(proteccion.fallos).hasSize(1);
    }

    static class CuentasDoble implements CuentasLoginPort {
        CuentaLogin cuenta;
        final List<String> orden = new ArrayList<>();

        @Override public Optional<CuentaLogin> buscarPorCorreo(String correo) {
            orden.add("buscar");
            assertThat(correo).isEqualTo("c@ejemplo.test");
            return Optional.ofNullable(cuenta);
        }

        @Override public Optional<CuentaLogin> bloquear(UUID id) {
            orden.add("bloquear");
            return Optional.ofNullable(cuenta).filter(c -> c.id().equals(id));
        }
    }

    static class CredencialesDoble implements CredencialesLoginPort {
        boolean coincide = true;
        boolean coincideSegunda = true;
        boolean caducada;
        final List<Optional<UUID>> preverificaciones = new ArrayList<>();
        final Deque<Boolean> confirmaciones = new ArrayDeque<>();
        final RevisionCredencial revision = new RevisionCredencial(UUID.randomUUID(), 3);

        @Override public PrevisionCredencial preverificar(Optional<UUID> usuario, SecretoEfimero secreto) {
            preverificaciones.add(usuario);
            boolean ok = preverificaciones.size() == 1 ? coincide : coincideSegunda;
            return new PrevisionCredencial(ok, revision);
        }

        @Override public boolean confirmarRevision(RevisionCredencial rev) {
            return confirmaciones.isEmpty() || confirmaciones.removeFirst();
        }

        @Override public boolean caducada(UUID u, Set<Rol> roles, Instant ahora) {
            return caducada;
        }
    }

    static class ProteccionDoble implements ProteccionLoginPort {
        boolean bloqueado;
        final List<Fallo> fallos = new ArrayList<>();
        final List<UUID> completados = new ArrayList<>();

        record Fallo(UUID usuario, SecretoEfimero secreto, ContextoPeticion contexto) {
        }

        @Override public boolean loginBloqueado(UUID u) {
            return bloqueado;
        }

        @Override public void registrarFallo(UUID u, SecretoEfimero intento, ContextoPeticion c) {
            fallos.add(new Fallo(u, intento, c));
        }

        @Override public void completarLogin(UUID u) {
            completados.add(u);
        }
    }

    static class MfaDoble implements MfaLoginPort {
        boolean efectivo;
        boolean porRoles;
        DesafioCreado desafio = new DesafioCreado("challenge-1", "EMAIL", 300);
        final List<UUID> desafiosCreados = new ArrayList<>();

        @Override public boolean esEfectivo(boolean bandera, Set<Rol> roles) {
            assertThat(bandera).isFalse();
            return porRoles ? roles.stream().anyMatch(Rol::esDeGestion) : efectivo;
        }

        @Override public DesafioCreado crearDesafio(UUID u, RevisionCredencial r) {
            desafiosCreados.add(u);
            return desafio;
        }
    }

    static class AutorizacionDoble implements AutorizacionSesionPort {
        RolesSesion roles = new RolesSesion(Set.of(Rol.CLIENTE), List.of());

        @Override public RolesSesion resolverRoles(UUID u) {
            return roles;
        }
    }

    static class SesionesDoble implements SesionesPort {
        SesionIniciada ultimaAbierta;
        IdentidadAutorizada ultimaIdentidad;

        @Override public SesionIniciada abrir(UUID u, IdentidadAutorizada i, ContextoPeticion c) {
            ultimaIdentidad = i;
            ultimaAbierta = new SesionIniciada("acceso", "refresco", 900, AHORA.plusSeconds(900),
                    AHORA.plusSeconds(7 * 24 * 3600),
                    new UsuarioAutenticado(u, i.nombreCompleto(), i.correo(), i.roles()));
            return ultimaAbierta;
        }

        @Override public ContextoSesion resolverAcceso(AccesoVerificado a) {
            throw new UnsupportedOperationException("fuera de T006");
        }

        @Override public RenovacionResultado renovar(SecretoRefresco s) {
            throw new UnsupportedOperationException("fuera de T006");
        }

        @Override public CierreResultado cerrar(SecretoRefresco s) {
            throw new UnsupportedOperationException("fuera de T006");
        }

        @Override public void revocarTodas(UUID u) {
            throw new UnsupportedOperationException("fuera de T006");
        }

        @Override public void revocarOtras(UUID u, UUID f) {
            throw new UnsupportedOperationException("fuera de T006");
        }
    }
}
