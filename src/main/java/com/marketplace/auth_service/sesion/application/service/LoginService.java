package com.marketplace.auth_service.sesion.application.service;

import com.marketplace.auth_service.sesion.application.dto.CredencialesLogin;
import com.marketplace.auth_service.sesion.application.dto.CuentaLogin;
import com.marketplace.auth_service.sesion.application.dto.DesafioCreado;
import com.marketplace.auth_service.sesion.application.dto.EstadoCuentaLogin;
import com.marketplace.auth_service.sesion.application.dto.IdentidadAutorizada;
import com.marketplace.auth_service.sesion.application.dto.PrevisionCredencial;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.sesion.application.dto.RevisionCredencial;
import com.marketplace.auth_service.sesion.application.dto.RolesSesion;
import com.marketplace.auth_service.sesion.application.dto.SecretoEfimero;
import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.dto.UsuarioAutenticado;
import com.marketplace.auth_service.sesion.application.port.in.LoginPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.port.out.AutorizacionSesionPort;
import com.marketplace.auth_service.sesion.application.port.out.CredencialesLoginPort;
import com.marketplace.auth_service.sesion.application.port.out.CuentasLoginPort;
import com.marketplace.auth_service.sesion.application.port.out.MfaLoginPort;
import com.marketplace.auth_service.sesion.application.port.out.ProteccionLoginPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Login con composición real (EP-02-T006, SPEC-05, ACUERDOS-INTERNOS §4). Java
 * puro: normaliza el correo, compara con Juan José (equivalente para
 * inexistentes), bloquea la cuenta con Eva, confirma la revisión (una sola
 * repetición sin efectos si cambió), registra el fallo elegible con Luis,
 * revalida roles con Sergio, caducidad con Juan José y MFA con Luis. Solo
 * CLIENTE sin MFA efectivo abre familia; el resto de ramas rechaza con el
 * mismo 401 o desafía sin tokens. La auditoría canónica se conecta en T007.
 */
public class LoginService implements LoginPort {

    private final CuentasLoginPort cuentas;
    private final CredencialesLoginPort credenciales;
    private final ProteccionLoginPort proteccion;
    private final MfaLoginPort mfa;
    private final AutorizacionSesionPort autorizacion;
    private final SesionesPort sesiones;
    private final Clock clock;

    public LoginService(CuentasLoginPort cuentas, CredencialesLoginPort credenciales,
                        ProteccionLoginPort proteccion, MfaLoginPort mfa,
                        AutorizacionSesionPort autorizacion, SesionesPort sesiones, Clock clock) {
        this.cuentas = Objects.requireNonNull(cuentas, "cuentas");
        this.credenciales = Objects.requireNonNull(credenciales, "credenciales");
        this.proteccion = Objects.requireNonNull(proteccion, "proteccion");
        this.mfa = Objects.requireNonNull(mfa, "mfa");
        this.autorizacion = Objects.requireNonNull(autorizacion, "autorizacion");
        this.sesiones = Objects.requireNonNull(sesiones, "sesiones");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ResultadoLogin ingresar(CredencialesLogin credencialesLogin, ContextoPeticion contexto) {
        Objects.requireNonNull(credencialesLogin, "credencialesLogin");
        Objects.requireNonNull(contexto, "contexto");
        String correo = normalizar(credencialesLogin.correo());
        SecretoEfimero secreto = credencialesLogin.contrasena();
        Optional<CuentaLogin> cuenta = cuentas.buscarPorCorreo(correo);
        PrevisionCredencial prevision =
                credenciales.preverificar(cuenta.map(CuentaLogin::id), secreto);
        if (!prevision.coincide()) {
            return falloElegible(cuenta, prevision, secreto, contexto);
        }
        UUID usuarioId = cuenta.map(CuentaLogin::id).orElse(null);
        if (usuarioId == null) {
            return new ResultadoLogin.Rechazado();
        }
        CuentaLogin bloqueada = cuentas.bloquear(usuarioId).orElse(null);
        if (bloqueada == null) {
            return new ResultadoLogin.Rechazado();
        }
        if (!credenciales.confirmarRevision(prevision.revision())) {
            PrevisionCredencial segunda = credenciales.preverificar(Optional.of(usuarioId), secreto);
            if (!segunda.coincide()) {
                return registrarYRechazar(usuarioId, bloqueada, secreto, contexto);
            }
            prevision = segunda;
        }
        return continuarAutenticado(usuarioId, bloqueada, prevision.revision(), contexto);
    }

    private ResultadoLogin falloElegible(Optional<CuentaLogin> cuenta, PrevisionCredencial prevision,
                                         SecretoEfimero secreto, ContextoPeticion contexto) {
        if (cuenta.isEmpty()) {
            return new ResultadoLogin.Rechazado();
        }
        UUID usuarioId = cuenta.get().id();
        CuentaLogin bloqueada = cuentas.bloquear(usuarioId).orElse(null);
        if (bloqueada == null) {
            return new ResultadoLogin.Rechazado();
        }
        if (!credenciales.confirmarRevision(prevision.revision())) {
            PrevisionCredencial segunda = credenciales.preverificar(Optional.of(usuarioId), secreto);
            if (segunda.coincide()) {
                return continuarAutenticado(usuarioId, bloqueada, segunda.revision(), contexto);
            }
        }
        return registrarYRechazar(usuarioId, bloqueada, secreto, contexto);
    }

    private ResultadoLogin registrarYRechazar(UUID usuarioId, CuentaLogin cuenta, SecretoEfimero secreto,
                                              ContextoPeticion contexto) {
        if (cuenta.estado() == EstadoCuentaLogin.ACTIVA) {
            proteccion.registrarFallo(usuarioId, secreto, contexto);
        }
        return new ResultadoLogin.Rechazado();
    }

    private ResultadoLogin continuarAutenticado(UUID usuarioId, CuentaLogin cuenta, RevisionCredencial revision,
                                                ContextoPeticion contexto) {
        Instant ahora = clock.instant();
        if (cuenta.estado() != EstadoCuentaLogin.ACTIVA || proteccion.loginBloqueado(usuarioId)) {
            return new ResultadoLogin.Rechazado();
        }
        RolesSesion roles = autorizacion.resolverRoles(usuarioId);
        if (credenciales.caducada(usuarioId, roles.roles(), ahora)) {
            return new ResultadoLogin.Caducada();
        }
        if (mfa.esEfectivo(cuenta.mfaHabilitado(), roles.roles())) {
            DesafioCreado desafio = mfa.crearDesafio(usuarioId, revision);
            return new ResultadoLogin.Desafio(desafio.comoRespuesta());
        }
        SesionIniciada abierta = sesiones.abrir(usuarioId,
                new IdentidadAutorizada(usuarioId, cuenta.nombreCompleto(), cuenta.correo(),
                        roles.codigosRoles(), roles.permisos()),
                contexto);
        proteccion.completarLogin(usuarioId);
        return new ResultadoLogin.Exito(abierta);
    }

    static String normalizar(String correo) {
        return correo.strip().toLowerCase(Locale.ROOT);
    }
}
