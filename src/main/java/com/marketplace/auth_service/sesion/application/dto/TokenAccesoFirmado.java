package com.marketplace.auth_service.sesion.application.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Acceso firmado por Sergio, sin decodificar el token. Sin token en logs. */
public record TokenAccesoFirmado(String token, UUID jti, Instant emitidoEn, Instant expiraEn) {
    public TokenAccesoFirmado {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(jti, "jti");
        Objects.requireNonNull(emitidoEn, "emitidoEn");
        Objects.requireNonNull(expiraEn, "expiraEn");
    }

    /** Vida en segundos, para el {@code expiresIn} de la respuesta. */
    public long expiresIn() {
        return expiraEn.getEpochSecond() - emitidoEn.getEpochSecond();
    }

    @Override
    public String toString() {
        return "TokenAccesoFirmado[jti=" + jti + ", expiraEn=" + expiraEn + "]";
    }
}
