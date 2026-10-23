package com.marketplace.auth_service.identidad.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Token de servicio firmado. {@code toString} no incluye el token. */
public record TokenServicioFirmado(String token, UUID jti, Instant emitidoEn, Instant expiraEn) {

    /** Vida en segundos, para el {@code expiresIn} de la respuesta. */
    public long expiresIn() {
        return expiraEn.getEpochSecond() - emitidoEn.getEpochSecond();
    }

    @Override
    public String toString() {
        return "TokenServicioFirmado[jti=" + jti + ", expiraEn=" + expiraEn + "]";
    }
}
