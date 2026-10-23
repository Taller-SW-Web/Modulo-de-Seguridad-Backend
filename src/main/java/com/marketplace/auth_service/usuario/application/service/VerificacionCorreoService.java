package com.marketplace.auth_service.usuario.application.service;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoReenvio;
import com.marketplace.auth_service.usuario.application.dto.ResultadoVerificacion;
import com.marketplace.auth_service.usuario.application.port.in.VerificacionCorreoPort;
import com.marketplace.auth_service.usuario.application.port.out.CorreoVerificacionPort;
import com.marketplace.auth_service.usuario.application.port.out.CuentasVerificacionPort;
import com.marketplace.auth_service.usuario.application.port.out.CuotaReenvioPort;
import com.marketplace.auth_service.usuario.application.port.out.EfectosVerificacionPort;
import com.marketplace.auth_service.usuario.application.port.out.TokensVerificacionPort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Verificación de correo y reenvío (SPEC-02, FEAT-01-T3).
 *
 * <ul>
 *   <li>Verificar, en una transacción: cuenta bloqueada y aún pendiente → token consumido →
 *       {@code ACTIVO} → {@code usuario.creado} por el outbox. Cualquier rechazo revierte, así la
 *       cuenta sigue pendiente y no se publica nada (ESC-02.2). Vencido y usado se auditan.</li>
 *   <li>Reenviar: la cuota se reserva por correo exista o no la cuenta (ESC-02.5); solo con cuota y
 *       una cuenta pendiente se emite un enlace nuevo, que invalida el anterior (RF-02.4).</li>
 * </ul>
 */
public final class VerificacionCorreoService implements VerificacionCorreoPort {

    private final TokensVerificacionPort tokens;
    private final CuentasVerificacionPort cuentas;
    private final CuotaReenvioPort cuota;
    private final CorreoVerificacionPort correoVerificacion;
    private final EfectosVerificacionPort efectos;
    private final Clock reloj;

    public VerificacionCorreoService(TokensVerificacionPort tokens, CuentasVerificacionPort cuentas,
                                     CuotaReenvioPort cuota, CorreoVerificacionPort correoVerificacion,
                                     EfectosVerificacionPort efectos, Clock reloj) {
        this.tokens = Objects.requireNonNull(tokens);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.cuota = Objects.requireNonNull(cuota);
        this.correoVerificacion = Objects.requireNonNull(correoVerificacion);
        this.efectos = Objects.requireNonNull(efectos);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public ResultadoVerificacion verificar(String tokenClaro, ContextoPeticion contexto) {
        Objects.requireNonNull(tokenClaro);
        Objects.requireNonNull(contexto);
        var token = tokens.localizar(tokenClaro).orElse(null);
        if (token == null) {
            return ResultadoVerificacion.INVALIDA; // sin cuenta conocida: no hay a quién atribuir el intento
        }
        Instant ahora = reloj.instant();
        var resultado = efectos.enTransaccion(() -> {
            if (token.usado()) return ResultadoVerificacion.YA_USADA;
            if (!token.vigenteEn(ahora)) return ResultadoVerificacion.EXPIRADA;
            // Cuenta antes que token: dos verificaciones simultáneas se ordenan por la fila de la cuenta.
            if (!cuentas.activarSiPendiente(token.usuarioId(), ahora)) return ResultadoVerificacion.YA_USADA;
            if (!tokens.consumir(token.id(), token.usuarioId(), ahora)) return ResultadoVerificacion.YA_USADA;
            efectos.publicarUsuarioCreado(token.usuarioId(), ahora);
            return ResultadoVerificacion.VERIFICADA;
        }, r -> r == ResultadoVerificacion.VERIFICADA);
        efectos.auditarVerificacion(token.usuarioId(), switch (resultado) {
            case VERIFICADA -> null;
            case EXPIRADA -> "ENLACE_EXPIRADO";
            case YA_USADA, INVALIDA -> "ENLACE_YA_USADO";
        }, contexto);
        return resultado;
    }

    @Override
    public ResultadoReenvio reenviar(String correo, ContextoPeticion contexto) {
        Objects.requireNonNull(correo);
        Objects.requireNonNull(contexto);
        String normalizado = correo.strip().toLowerCase(Locale.ROOT);
        Instant ahora = reloj.instant();
        try {
            return efectos.enTransaccion(() -> {
                Duration espera = cuota.reservar(normalizado, ahora);
                if (!espera.isZero()) {
                    return new ResultadoReenvio.Limitado(Math.max(1, (espera.toMillis() + 999) / 1000));
                }
                cuentas.buscarPendiente(normalizado).ifPresent(cuenta ->
                        correoVerificacion.enviar(cuenta.id(), cuenta.correo(), cuenta.nombres()));
                return ResultadoReenvio.Aceptado.ACEPTADO;
            }, r -> true);
        } catch (VerificacionNoDisponibleException colaCaida) {
            // Solo puede fallar con una cuenta pendiente: un 503 delataría que el correo está registrado.
            // Sin la clave de la cuota (CuotaNoDisponibleException) falla con cualquier correo y sí da 503.
            return ResultadoReenvio.Aceptado.ACEPTADO;
        }
    }
}
