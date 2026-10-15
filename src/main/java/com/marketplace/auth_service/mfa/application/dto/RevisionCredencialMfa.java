package com.marketplace.auth_service.mfa.application.dto;

import java.util.Objects;
import java.util.UUID;

/** Revisión ya preverificada por Juan José; no es prueba de OTP ni de sesión. */
public record RevisionCredencialMfa(UUID usuarioId, UUID credencialId, long version) {
    public RevisionCredencialMfa {
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(credencialId);
        if (version < 0) throw new IllegalArgumentException("Revisión de credencial inválida");
    }
}
