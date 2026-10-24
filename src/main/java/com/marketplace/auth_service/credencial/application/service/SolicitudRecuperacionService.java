package com.marketplace.auth_service.credencial.application.service;

import com.marketplace.auth_service.credencial.application.dto.CuentaRecuperacion;
import com.marketplace.auth_service.credencial.application.dto.ResultadoSolicitudRecuperacion;
import com.marketplace.auth_service.credencial.application.port.in.SolicitarRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.AuditoriaRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.CorreoRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.CuentasRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.CuotaRecuperacionPort;
import com.marketplace.auth_service.credencial.domain.exception.SolicitudRecuperacionNoDisponibleException;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** Caso de uso neutro ante la existencia de la cuenta (SPEC-08, FEAT-03-T2). */
public final class SolicitudRecuperacionService implements SolicitarRecuperacionPort {
    private final CuentasRecuperacionPort cuentas;
    private final CuotaRecuperacionPort cuota;
    private final CorreoRecuperacionPort correo;
    private final AuditoriaRecuperacionPort auditoria;
    private final Clock reloj;

    public SolicitudRecuperacionService(CuentasRecuperacionPort cuentas, CuotaRecuperacionPort cuota,
                                        CorreoRecuperacionPort correo, AuditoriaRecuperacionPort auditoria,
                                        Clock reloj) {
        this.cuentas = Objects.requireNonNull(cuentas);
        this.cuota = Objects.requireNonNull(cuota);
        this.correo = Objects.requireNonNull(correo);
        this.auditoria = Objects.requireNonNull(auditoria);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public ResultadoSolicitudRecuperacion solicitar(String correoSolicitado, ContextoPeticion contexto) {
        Objects.requireNonNull(contexto, "contexto");
        String normalizado = normalizar(correoSolicitado);
        var ahora = reloj.instant();
        Duration espera = cuota.reservar(normalizado, ahora);
        if (!espera.isZero()) {
            auditoria.registrar(null, false, contexto, ahora);
            return new ResultadoSolicitudRecuperacion.Limite(Math.max(1, espera.toSeconds()));
        }

        CuentaRecuperacion cuenta = cuentas.buscar(normalizado).orElse(null);
        if (cuenta != null && cuenta.elegible()) {
            try {
                correo.emitirYEnviar(cuenta, ahora);
            } catch (SolicitudRecuperacionNoDisponibleException indisponible) {
                // La reserva ya quedó confirmada; no revelar si falló para una cuenta real.
                auditoria.registrar(cuenta.usuarioId(), true, contexto, ahora);
                return new ResultadoSolicitudRecuperacion.Aceptada();
            }
        }
        // El mismo código y cuerpo se devuelve para cuenta elegible, no elegible o inexistente.
        auditoria.registrar(cuenta == null ? null : cuenta.usuarioId(), true, contexto, ahora);
        return new ResultadoSolicitudRecuperacion.Aceptada();
    }

    static String normalizar(String correo) {
        if (correo == null || correo.isBlank() || correo.length() > 254) {
            throw new IllegalArgumentException("Correo inválido");
        }
        return correo.strip().toLowerCase(Locale.ROOT);
    }
}
