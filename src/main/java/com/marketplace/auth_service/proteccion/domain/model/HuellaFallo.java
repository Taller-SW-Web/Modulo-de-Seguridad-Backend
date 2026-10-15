package com.marketplace.auth_service.proteccion.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tag HMAC, jamás la contraseña. No se exporta en HTTP/auditoría/eventos. */
public record HuellaFallo(UUID usuarioId, UUID rachaId, String huellaHmac, Instant creadoEn) {
    public HuellaFallo {
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(rachaId);
        Objects.requireNonNull(creadoEn);
        if (huellaHmac == null || !huellaHmac.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Formato de huella inválido");
        }
    }
    @Override public String toString() {
        return "HuellaFallo[usuarioId=" + usuarioId + ", rachaId=" + rachaId + "]";
    }
}
