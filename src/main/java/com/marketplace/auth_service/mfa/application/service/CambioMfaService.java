package com.marketplace.auth_service.mfa.application.service;

import com.marketplace.auth_service.mfa.application.dto.CuentaMfaSnapshot;
import com.marketplace.auth_service.mfa.application.dto.ResultadoCambioMfa;
import com.marketplace.auth_service.mfa.application.port.in.CambioMfaPort;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.mfa.domain.model.*;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import static com.marketplace.auth_service.mfa.application.dto.ResultadoCambioMfa.error;

/** SPEC-10. Cuenta → familia → cambio → OTP; el decorador confirma también los rechazos con intentos. */
public final class CambioMfaService implements CambioMfaPort {
    private final CuentasMfaPort cuentas;
    private final RolesMfaPort roles;
    private final PersistenciaCambioMfaPort cambios;
    private final PersistenciaMfaPort otp;
    private final CriptografiaMfaPort criptografia;
    private final EfectosCambioMfaPort efectos;
    private final Clock reloj;

    public CambioMfaService(CuentasMfaPort cuentas, RolesMfaPort roles, PersistenciaCambioMfaPort cambios,
            PersistenciaMfaPort otp, CriptografiaMfaPort criptografia, EfectosCambioMfaPort efectos, Clock reloj) {
        this.cuentas = cuentas; this.roles = roles; this.cambios = cambios; this.otp = otp;
        this.criptografia = criptografia; this.efectos = efectos; this.reloj = reloj;
    }

    @Override public ResultadoCambioMfa solicitar(ActorUsuario actor, String accion, ContextoPeticion contexto) {
        Sesion sesion = preparar(actor);
        if (sesion == null) return error("TOKEN_INVALIDO");
        if (!"HABILITAR".equals(accion) && !"DESHABILITAR".equals(accion)) return error("VALIDACION");
        boolean habilitar = "HABILITAR".equals(accion);
        var rechazo = politica(actor, habilitar, sesion.cuenta(), contexto);
        if (rechazo != null) return rechazo;
        var limite = limite(actor.sub());
        if (limite != null) return limite;
        Instant ahora = reloj.instant();
        var token = criptografia.generarDesafio();
        cambios.invalidarPendientes(actor.sub(), ahora);
        CambioMfa cambio = new CambioMfa(UUID.randomUUID(), actor.sub(), sesion.familia(), habilitar,
                token.hashToken(), ahora, ahora.plusSeconds(600), false, null, null);
        cambios.guardar(cambio);
        return enviar(cambio, token.token(), actor, contexto);
    }

    @Override public ResultadoCambioMfa reenviar(ActorUsuario actor, String token, ContextoPeticion contexto) {
        CambioMfa cambio = cambioVigente(actor, token);
        if (cambio == null) return error("TOKEN_INVALIDO");
        var rechazo = politica(actor, cambio.habilitar(), cuentas.leer(actor.sub()).orElseThrow(), contexto);
        if (rechazo != null) return rechazo;
        var limite = limite(actor.sub());
        return limite == null ? enviar(cambio, token, actor, contexto) : limite;
    }

    @Override public ResultadoCambioMfa confirmar(ActorUsuario actor, String token, String codigo, ContextoPeticion contexto) {
        CambioMfa cambio = cambioVigente(actor, token);
        if (cambio == null) return error("TOKEN_INVALIDO");
        var rechazo = politica(actor, cambio.habilitar(), cuentas.leer(actor.sub()).orElseThrow(), contexto);
        if (rechazo != null) return rechazo;
        Otp actual = cambio.otpActualId() == null ? null : otp.bloquearOtp(cambio.otpActualId()).orElse(null);
        if (!pertenece(actual, cambio) || actual.usado()) return error("TOKEN_INVALIDO");
        Instant ahora = reloj.instant();
        if (actual.intentos() >= Otp.MAX_INTENTOS || actual.invalidadoEn() != null) return error("OTP_INTENTOS_AGOTADOS");
        if (!ahora.isBefore(actual.expiraEn())) return error("OTP_EXPIRADO");
        boolean correcto = criptografia.coincideOtp(actual.contexto(), codigo, actual.hashCodigo(), actual.hashKeyId());
        if (!correcto) {
            int intentos = actual.intentos() + 1;
            otp.guardarOtp(new Otp(actual.contexto(), actual.hashCodigo(), actual.hashKeyId(), actual.canal(),
                    actual.destino(), actual.creadoEn(), actual.expiraEn(), false, intentos,
                    intentos >= Otp.MAX_INTENTOS ? ahora : null));
            efectos.auditar("OTP_FALLIDO", false, false, actor, contexto, ahora);
            return intentos >= Otp.MAX_INTENTOS ? error("OTP_INTENTOS_AGOTADOS")
                    : new ResultadoCambioMfa.Error("CODIGO_INVALIDO", Otp.MAX_INTENTOS - intentos, null);
        }
        otp.guardarOtp(new Otp(actual.contexto(), actual.hashCodigo(), actual.hashKeyId(), actual.canal(),
                actual.destino(), actual.creadoEn(), actual.expiraEn(), true, actual.intentos(), null));
        cambios.guardar(cambio.consumir());
        efectos.guardarEstado(actor.sub(), cambio.habilitar());
        efectos.auditar(cambio.habilitar() ? "MFA_ACTIVADO" : "MFA_DESACTIVADO", true, true, actor, contexto, ahora);
        efectos.avisarCambio(actual.destino(), cambio.habilitar());
        efectos.auditar("OTP_VERIFICADO", true, false, actor, contexto, ahora);
        return ResultadoCambioMfa.Confirmado.EXITO;
    }

    private ResultadoCambioMfa enviar(CambioMfa cambio, String token, ActorUsuario actor, ContextoPeticion contexto) {
        Instant ahora = reloj.instant();
        if (!cambio.vigente(ahora)) return error("TOKEN_INVALIDO");
        if (cambio.otpActualId() != null) {
            Otp anterior = otp.bloquearOtp(cambio.otpActualId()).orElseThrow();
            otp.guardarOtp(new Otp(anterior.contexto(), anterior.hashCodigo(), anterior.hashKeyId(), anterior.canal(),
                    anterior.destino(), anterior.creadoEn(), anterior.expiraEn(), anterior.usado(), anterior.intentos(), ahora));
        }
        String correo = cuentas.contacto(actor.sub()).orElseThrow().correo();
        var contextoOtp = new ContextoOtp(actor.sub(), cambio.id(), UUID.randomUUID(), ContextoOtp.CAMBIO);
        var codigo = criptografia.generarOtp(contextoOtp);
        otp.guardarOtp(Otp.nuevo(contextoOtp, codigo.hashCodigo(), codigo.hashKeyId(), CanalOtp.EMAIL, correo, ahora));
        cambios.guardar(cambio.conCodigo(contextoOtp.otpId()));
        efectos.enviarCodigo(correo, codigo.codigo());
        efectos.auditar("OTP_SOLICITADO", true, false, actor, contexto, ahora);
        return new ResultadoCambioMfa.Enviado(token, "EMAIL", OtpLoginService.enmascarar(correo, CanalOtp.EMAIL), 300,
                (int) Math.max(1, Duration.between(ahora, cambio.expiraEn()).toSeconds()));
    }

    private ResultadoCambioMfa.Error politica(ActorUsuario actor, boolean habilitar, CuentaMfaSnapshot cuenta,
            ContextoPeticion contexto) {
        var actuales = roles.leer(actor.sub());
        if (!habilitar && PoliticaMfa.obligatorio(actuales)) {
            efectos.auditar("MFA_DESACTIVADO", false, true, actor, contexto, reloj.instant());
            return error("MFA_OBLIGATORIO");
        }
        return PoliticaMfa.efectivo(cuenta.mfaVoluntario(), actuales) == habilitar ? error("VALIDACION") : null;
    }

    private ResultadoCambioMfa.Error limite(UUID usuario) {
        Instant ahora = reloj.instant();
        Instant inicio = ahora.minusSeconds(900);
        if (otp.contarSolicitudes(usuario, inicio) < 3) return null;
        Instant primera = otp.primeraSolicitudEnVentana(usuario, inicio).orElse(ahora);
        return new ResultadoCambioMfa.Error("DEMASIADAS_SOLICITUDES", null,
                Math.max(1, Duration.between(ahora, primera.plusSeconds(900)).toSeconds() + 1));
    }

    private Sesion preparar(ActorUsuario actor) {
        if (actor == null || cuentas.leer(actor.sub()).isEmpty()) return null;
        cuentas.bloquear(actor.sub());
        var cuenta = cuentas.leer(actor.sub()).orElse(null);
        if (cuenta == null || !cuenta.disponible() || !cuenta.correoVerificado()) return null;
        return efectos.familia(actor).map(f -> new Sesion(cuenta, f)).orElse(null);
    }

    private CambioMfa cambioVigente(ActorUsuario actor, String token) {
        Sesion sesion = preparar(actor);
        if (sesion == null) return null;
        String hash;
        try { hash = criptografia.hashDesafio(token); }
        catch (IllegalArgumentException invalido) { return null; }
        var orientativo = cambios.buscarPorHash(hash).orElse(null);
        if (orientativo == null || !orientativo.usuarioId().equals(actor.sub())
                || !orientativo.familiaId().equals(sesion.familia())) return null;
        return cambios.bloquear(orientativo.id()).filter(c -> c.vigente(reloj.instant())
                && c.usuarioId().equals(actor.sub()) && c.familiaId().equals(sesion.familia())
                && c.hashToken().equals(hash)).orElse(null);
    }

    private static boolean pertenece(Otp codigo, CambioMfa cambio) {
        return codigo != null && ContextoOtp.CAMBIO.equals(codigo.contexto().proposito())
                && codigo.contexto().usuarioId().equals(cambio.usuarioId())
                && codigo.contexto().desafioId().equals(cambio.id());
    }
    private record Sesion(CuentaMfaSnapshot cuenta, UUID familia) { }
}
