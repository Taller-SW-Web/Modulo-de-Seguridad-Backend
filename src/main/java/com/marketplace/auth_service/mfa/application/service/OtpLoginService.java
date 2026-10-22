package com.marketplace.auth_service.mfa.application.service;

import com.marketplace.auth_service.mfa.application.dto.CanalMfa;
import com.marketplace.auth_service.mfa.application.dto.ContactoMfa;
import com.marketplace.auth_service.mfa.application.dto.ResultadoSolicitudOtp;
import com.marketplace.auth_service.mfa.application.dto.ResultadoVerificacionOtp;
import com.marketplace.auth_service.mfa.application.dto.RevisionCredencialMfa;
import com.marketplace.auth_service.mfa.application.port.in.OtpLoginPort;
import com.marketplace.auth_service.mfa.application.port.out.CriptografiaMfaPort;
import com.marketplace.auth_service.mfa.application.port.out.CuentasMfaPort;
import com.marketplace.auth_service.mfa.application.port.out.EntregaOtpPort;
import com.marketplace.auth_service.mfa.application.port.out.HechosMfaPort;
import com.marketplace.auth_service.mfa.application.port.out.PersistenciaMfaPort;
import com.marketplace.auth_service.mfa.domain.model.CanalOtp;
import com.marketplace.auth_service.mfa.domain.model.ContextoOtp;
import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import com.marketplace.auth_service.mfa.domain.model.Otp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * OTP del desafío de login (EP-04-T003 solicitar, EP-04-T004 verificar; SPEC-09).
 *
 * <p>Primero se valida el desafío sin locks ({@link ValidadorDesafioLogin}: vigente, cuenta
 * disponible y verificada, MFA aún efectivo y misma revisión de credencial); después, en
 * orden: cuenta → desafío → código. Solo se guarda el HMAC del código; el código en claro
 * va al adaptador de entrega, que encola EMAIL cifrado en el mismo commit.
 */
public final class OtpLoginService implements OtpLoginPort {

    static final Duration VENTANA_CUOTA = Duration.ofMinutes(15);
    static final int MAX_SOLICITUDES = 3;

    private final ValidadorDesafioLogin validador;
    private final CuentasMfaPort cuentas;
    private final PersistenciaMfaPort persistencia;
    private final CriptografiaMfaPort criptografia;
    private final EntregaOtpPort entrega;
    private final Clock reloj;
    private final Supplier<UUID> identificadores;
    private final HechosMfaPort hechos;

    public OtpLoginService(ValidadorDesafioLogin validador, CuentasMfaPort cuentas, PersistenciaMfaPort persistencia,
                           CriptografiaMfaPort criptografia, EntregaOtpPort entrega, Clock reloj,
                           Supplier<UUID> identificadores) {
        this(validador, cuentas, persistencia, criptografia, entrega, reloj, identificadores, usuario -> { });
    }

    public OtpLoginService(ValidadorDesafioLogin validador, CuentasMfaPort cuentas, PersistenciaMfaPort persistencia,
                           CriptografiaMfaPort criptografia, EntregaOtpPort entrega, Clock reloj,
                           Supplier<UUID> identificadores, HechosMfaPort hechos) {
        this.hechos = Objects.requireNonNull(hechos);
        this.validador = Objects.requireNonNull(validador);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.persistencia = Objects.requireNonNull(persistencia);
        this.criptografia = Objects.requireNonNull(criptografia);
        this.entrega = Objects.requireNonNull(entrega);
        this.reloj = Objects.requireNonNull(reloj);
        this.identificadores = Objects.requireNonNull(identificadores);
    }

    @Override
    public ResultadoSolicitudOtp solicitar(String challengeToken, CanalMfa canalPedido) {
        Optional<DesafioMfa> desafio = desafioBloqueado(challengeToken);
        if (desafio.isEmpty()) {
            return ResultadoSolicitudOtp.Rechazo.DESAFIO_INVALIDO;
        }
        DesafioMfa actual = desafio.get();
        CanalOtp canal = canalPedido == CanalMfa.SMS ? CanalOtp.SMS : CanalOtp.EMAIL;
        ContactoMfa contacto = cuentas.contacto(actual.usuarioId()).orElse(null);
        if (contacto == null) {
            return ResultadoSolicitudOtp.Rechazo.DESAFIO_INVALIDO;
        }
        String destino = canal == CanalOtp.SMS ? contacto.celular() : contacto.correo();
        if (destino == null || destino.isBlank()) {
            return ResultadoSolicitudOtp.Rechazo.SIN_CELULAR; // RF-09.15: sin enviar ni consumir cuota
        }

        Instant ahora = reloj.instant();
        Instant inicioVentana = ahora.minus(VENTANA_CUOTA);
        if (persistencia.contarSolicitudes(actual.usuarioId(), inicioVentana) >= MAX_SOLICITUDES) {
            Instant primera = persistencia.primeraSolicitudEnVentana(actual.usuarioId(), inicioVentana).orElse(ahora);
            long espera = Math.max(1, Duration.between(ahora, primera.plus(VENTANA_CUOTA)).toSeconds() + 1);
            return new ResultadoSolicitudOtp.Limite(espera);
        }

        // RF-09.14: el reenvío invalida el código anterior del mismo desafío.
        for (Otp previo : persistencia.listarCodigosDelDesafio(actual.id())) {
            if (previo.vigenteEn(ahora)) {
                persistencia.guardarOtp(new Otp(previo.contexto(), previo.hashCodigo(), previo.hashKeyId(),
                        previo.canal(), previo.destino(), previo.creadoEn(), previo.expiraEn(), previo.usado(),
                        previo.intentos(), ahora));
            }
        }
        ContextoOtp contexto = new ContextoOtp(actual.usuarioId(), actual.id(), identificadores.get());
        var generado = criptografia.generarOtp(contexto);
        persistencia.guardarOtp(Otp.nuevo(contexto, generado.hashCodigo(), generado.hashKeyId(), canal, destino, ahora));
        entrega.programar(canal, destino, generado.codigo());
        hechos.otpSolicitado(actual.usuarioId());

        int restanteDesafio = (int) Math.max(1, Math.min(300, Duration.between(ahora, actual.expiraEn()).toSeconds()));
        return new ResultadoSolicitudOtp.Enviado(CanalMfa.valueOf(canal.name()), enmascarar(destino, canal),
                (int) Otp.VIGENCIA.toSeconds(), restanteDesafio);
    }

    @Override
    public ResultadoVerificacionOtp verificar(String challengeToken, String codigo) {
        Optional<DesafioMfa> desafio = desafioBloqueado(challengeToken);
        if (desafio.isEmpty()) {
            return ResultadoVerificacionOtp.Rechazo.DESAFIO_INVALIDO;
        }
        DesafioMfa actual = desafio.get();
        Instant ahora = reloj.instant();
        Optional<Otp> ultimo = persistencia.listarCodigosDelDesafio(actual.id()).stream()
                .max(Comparator.comparing(Otp::creadoEn));
        if (ultimo.isEmpty()) {
            return new ResultadoVerificacionOtp.CodigoInvalido(0);
        }
        Otp otp = persistencia.bloquearOtp(ultimo.get().contexto().otpId()).orElse(null);
        if (otp == null || otp.usado()) {
            return new ResultadoVerificacionOtp.CodigoInvalido(0); // ESC-09.10: reutilización
        }
        if (otp.invalidadoEn() != null || otp.intentos() >= Otp.MAX_INTENTOS) {
            return ResultadoVerificacionOtp.Rechazo.INTENTOS_AGOTADOS;
        }
        if (!ahora.isBefore(otp.expiraEn())) {
            return ResultadoVerificacionOtp.Rechazo.CODIGO_EXPIRADO; // 410, sin cambiar contadores
        }
        boolean coincide = codigo != null && codigo.matches("[0-9]{6}")
                && criptografia.coincideOtp(otp.contexto(), codigo, otp.hashCodigo(), otp.hashKeyId());
        if (!coincide) {
            int intentos = otp.intentos() + 1;
            persistencia.guardarOtp(new Otp(otp.contexto(), otp.hashCodigo(), otp.hashKeyId(), otp.canal(),
                    otp.destino(), otp.creadoEn(), otp.expiraEn(), false, intentos,
                    intentos >= Otp.MAX_INTENTOS ? ahora : null));
            return intentos >= Otp.MAX_INTENTOS
                    ? ResultadoVerificacionOtp.Rechazo.INTENTOS_AGOTADOS
                    : new ResultadoVerificacionOtp.CodigoInvalido(Otp.MAX_INTENTOS - intentos);
        }
        // RF-09.7: un solo uso del código y del desafío.
        persistencia.guardarOtp(new Otp(otp.contexto(), otp.hashCodigo(), otp.hashKeyId(), otp.canal(),
                otp.destino(), otp.creadoEn(), otp.expiraEn(), true, otp.intentos(), null));
        persistencia.guardarDesafio(new DesafioMfa(actual.id(), actual.usuarioId(), actual.credencialId(),
                actual.credencialVersion(), actual.hashToken(), actual.creadoEn(), actual.expiraEn(), true));
        return new ResultadoVerificacionOtp.Verificado(actual.usuarioId(),
                new RevisionCredencialMfa(actual.usuarioId(), actual.credencialId(), actual.credencialVersion()));
    }

    /** Valida sin locks y, si sigue vigente, bloquea cuenta y desafío y vuelve a comprobarlo. */
    private Optional<DesafioMfa> desafioBloqueado(String challengeToken) {
        if (challengeToken == null || challengeToken.isBlank()) {
            return Optional.empty();
        }
        Optional<DesafioMfa> orientativo = validador.localizarVigente(challengeToken);
        if (orientativo.isEmpty()) {
            return Optional.empty();
        }
        cuentas.bloquear(orientativo.get().usuarioId());
        return persistencia.bloquearDesafio(orientativo.get().id())
                .filter(d -> d.vigenteEn(reloj.instant()) && d.hashToken().equals(orientativo.get().hashToken()));
    }

    static String enmascarar(String destino, CanalOtp canal) {
        if (canal == CanalOtp.SMS) {
            return destino.length() <= 3 ? "***" : "*".repeat(destino.length() - 3) + destino.substring(destino.length() - 3);
        }
        int arroba = destino.indexOf('@');
        if (arroba <= 1) {
            return "***" + (arroba >= 0 ? destino.substring(arroba) : "");
        }
        return destino.charAt(0) + "***" + destino.charAt(arroba - 1) + destino.substring(arroba);
    }
}
