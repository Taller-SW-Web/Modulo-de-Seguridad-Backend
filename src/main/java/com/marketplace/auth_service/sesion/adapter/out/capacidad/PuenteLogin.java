package com.marketplace.auth_service.sesion.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.credencial.application.dto.RevisionCredencial;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.mfa.application.dto.ResultadoCreacionDesafio;
import com.marketplace.auth_service.mfa.application.dto.RevisionCredencialMfa;
import com.marketplace.auth_service.mfa.application.dto.ResultadoVerificacionOtp;
import com.marketplace.auth_service.mfa.application.port.in.MfaPort;
import com.marketplace.auth_service.mfa.application.port.in.OtpLoginPort;
import com.marketplace.auth_service.proteccion.application.dto.HuellaFallo;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import com.marketplace.auth_service.sesion.application.dto.CuentaLogin;
import com.marketplace.auth_service.sesion.application.dto.DesafioEmitido;
import com.marketplace.auth_service.sesion.application.dto.PreverificacionLogin;
import com.marketplace.auth_service.sesion.application.dto.RevisionLogin;
import com.marketplace.auth_service.sesion.application.dto.RolesLogin;
import com.marketplace.auth_service.sesion.application.dto.VerificacionOtpLogin;
import com.marketplace.auth_service.sesion.application.port.out.CapacidadesLoginPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puente del login hacia cuentas (Eva), credenciales (Juan José), protección y MFA (Luis)
 * y autorización (Sergio). Solo traduce entre sus DTO exportados; no decide reglas ajenas.
 */
public final class PuenteLogin implements CapacidadesLoginPort {

    private final CuentasPort cuentas;
    private final CredencialesPort credenciales;
    private final ProteccionPort proteccion;
    private final AutorizacionPort autorizacion;
    private final MfaPort mfa;
    private final OtpLoginPort otp;
    private final AuditoriaPort auditoria;

    public PuenteLogin(CuentasPort cuentas, CredencialesPort credenciales, ProteccionPort proteccion,
                       AutorizacionPort autorizacion, MfaPort mfa, OtpLoginPort otp, AuditoriaPort auditoria) {
        this.otp = otp;
        this.auditoria = auditoria;
        this.cuentas = cuentas;
        this.credenciales = credenciales;
        this.proteccion = proteccion;
        this.autorizacion = autorizacion;
        this.mfa = mfa;
    }

    @Override
    public Optional<CuentaLogin> buscarCuenta(String correoNormalizado) {
        return cuentas.buscarPorCorreoNormalizado(correoNormalizado).map(PuenteLogin::aCuenta);
    }

    @Override
    public PreverificacionLogin preverificar(Optional<UUID> usuarioId, String contrasena) {
        var previa = credenciales.preverificar(usuarioId, new SecretoEfimero(contrasena));
        return new PreverificacionLogin(previa.coincide(), previa.revision()
                .map(r -> new RevisionLogin(r.usuarioId(), r.credencialId(), r.version())));
    }

    @Override
    public void bloquearCatalogoAdministrador() {
        autorizacion.bloquearCatalogoAdministrador();
    }

    @Override
    public CuentaLogin bloquearCuenta(UUID usuarioId) {
        return aCuenta(cuentas.bloquear(usuarioId));
    }

    @Override
    public boolean proteccionPermiteLogin(UUID usuarioId, Instant ahora) {
        return cuentas.leer(usuarioId)
                .map(c -> proteccion.evaluar(aProteccion(c), ahora).permiteLogin())
                .orElse(false);
    }

    @Override
    public void registrarFallo(Optional<UUID> usuarioId, String correoIntentado, String contrasena,
                               ContextoPeticion contexto) {
        var cuenta = usuarioId.flatMap(cuentas::leer).map(PuenteLogin::aProteccion).orElse(null);
        byte[] secreto = contrasena.getBytes(StandardCharsets.UTF_8);
        try (HuellaFallo huella = new HuellaFallo(secreto, correoIntentado)) {
            proteccion.registrarFallo(cuenta, huella, contexto);
        } finally {
            Arrays.fill(secreto, (byte) 0);
        }
    }

    @Override
    public boolean confirmarRevision(RevisionLogin revision) {
        return credenciales.confirmarRevision(
                new RevisionCredencial(revision.usuarioId(), revision.credencialId(), revision.version()));
    }

    @Override
    public RolesLogin resolverRoles(UUID usuarioId) {
        var resueltos = autorizacion.resolver(usuarioId);
        return new RolesLogin(resueltos.codigosRoles(), resueltos.codigosPermisos());
    }

    @Override
    public boolean mfaEfectivo(boolean voluntario, RolesLogin roles) {
        return mfa.esEfectivo(voluntario, aRoles(roles));
    }

    @Override
    public Optional<DesafioEmitido> crearDesafio(UUID usuarioId, RevisionLogin revision) {
        var resultado = mfa.crearDesafio(usuarioId,
                new RevisionCredencialMfa(revision.usuarioId(), revision.credencialId(), revision.version()));
        if (resultado instanceof ResultadoCreacionDesafio.Creado creado) {
            var d = creado.desafio();
            return Optional.of(new DesafioEmitido(d.challengeToken(), d.canal().name(), d.expiraEn()));
        }
        return Optional.empty();
    }

    @Override
    public boolean contrasenaCaducada(UUID usuarioId, RolesLogin roles, Instant ahora) {
        return credenciales.evaluarCaducidad(usuarioId, aRoles(roles), ahora);
    }

    @Override
    public void completarLogin(UUID usuarioId) {
        proteccion.completarLogin(usuarioId);
    }

    @Override
    public void auditar(String accion, boolean exito, Optional<UUID> usuarioId, ContextoPeticion contexto) {
        auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), accion, exito ? "EXITO" : "FALLO",
                usuarioId.isPresent() ? "USUARIO" : "SISTEMA", usuarioId.map(UUID::toString).orElse(null),
                usuarioId.orElse(null), Instant.now(), contexto.ip(), contexto.agente(), java.util.Map.of()));
    }

    @Override
    public VerificacionOtpLogin verificarOtp(String challengeToken, String codigo) {
        return switch (otp.verificar(challengeToken, codigo)) {
            case ResultadoVerificacionOtp.Verificado v -> new VerificacionOtpLogin.Verificada(v.usuarioId(),
                    new RevisionLogin(v.revision().usuarioId(), v.revision().credencialId(), v.revision().version()));
            case ResultadoVerificacionOtp.CodigoInvalido c -> new VerificacionOtpLogin.CodigoInvalido(c.intentosRestantes());
            case ResultadoVerificacionOtp.Rechazo r -> switch (r) {
                case DESAFIO_INVALIDO -> VerificacionOtpLogin.Rechazo.DESAFIO_INVALIDO;
                case INTENTOS_AGOTADOS -> VerificacionOtpLogin.Rechazo.INTENTOS_AGOTADOS;
                case CODIGO_EXPIRADO -> VerificacionOtpLogin.Rechazo.CODIGO_EXPIRADO;
            };
        };
    }

    private static CuentaLogin aCuenta(CuentaSnapshot c) {
        return new CuentaLogin(c.id(), c.correo(), c.nombreCompleto(),
                "ACTIVO".equals(c.estadoBase()) && c.correoVerificado(), c.mfaHabilitado());
    }

    private static com.marketplace.auth_service.proteccion.application.dto.CuentaSnapshot aProteccion(CuentaSnapshot c) {
        return new com.marketplace.auth_service.proteccion.application.dto.CuentaSnapshot(c.id(), c.correo(),
                c.estadoBase(), c.bloqueosSeguidos(), c.intentosFallidosConsecutivos());
    }

    private static Set<Rol> aRoles(RolesLogin roles) {
        Set<Rol> conjunto = EnumSet.noneOf(Rol.class);
        roles.roles().forEach(codigo -> conjunto.add(Rol.valueOf(codigo)));
        return conjunto;
    }
}
