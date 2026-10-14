package com.marketplace.auth_service.identidad.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Acceso firmado. {@code jti} y {@code expiraEn} permiten a sesiones (EP-02)
 * registrar la relación acceso→familia sin decodificar el token.
 * {@code toString} no incluye el token.
 */
public record AccesoFirmado(String token, UUID jti, Instant emitidoEn, Instant expiraEn) {

    /** Vida en segundos, para el {@code expiresIn} de la respuesta de login. */
    public long expiresIn() {
        return expiraEn.getEpochSecond() - emitidoEn.getEpochSecond();
    }

    @Override
    public String toString() {
        return "AccesoFirmado[jti=" + jti + ", emitidoEn=" + emitidoEn + ", expiraEn=" + expiraEn + "]";
    }
}
