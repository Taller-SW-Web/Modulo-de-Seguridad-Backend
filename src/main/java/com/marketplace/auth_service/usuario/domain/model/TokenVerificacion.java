package com.marketplace.auth_service.usuario.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Token de verificación localizado por su hash; nunca lleva el valor claro ni el hash. */
public record TokenVerificacion(UUID id, UUID usuarioId, Instant expiraEn, boolean usado) {
    public TokenVerificacion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(expiraEn, "expiraEn");
    }

    public boolean vigenteEn(Instant instante) {
        return instante.isBefore(expiraEn);
    }
}
