package com.marketplace.auth_service.proteccion.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** No contiene contadores de usuario: su único dueño físico es Eva. */
public record RachaFallo(UUID usuarioId, UUID rachaId, String hashKeyId, Instant creadoEn) {
    public RachaFallo {
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(rachaId);
        Objects.requireNonNull(creadoEn);
        if (hashKeyId == null || hashKeyId.isBlank() || hashKeyId.length() > 50
                || !hashKeyId.equals(hashKeyId.strip())) {
            throw new IllegalArgumentException("Identificador de clave inválido");
        }
    }
    @Override public String toString() {
        return "RachaFallo[usuarioId=" + usuarioId + ", rachaId=" + rachaId + "]";
    }
}
