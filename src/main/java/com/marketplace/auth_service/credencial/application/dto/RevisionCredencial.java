package com.marketplace.auth_service.credencial.application.dto;

import java.util.Objects;
import java.util.UUID;

/** credencialId es la PK real usuario_id de la persistencia vigente, no otro UUID. */
public record RevisionCredencial(UUID usuarioId, UUID credencialId, long version) {
    public RevisionCredencial {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(credencialId, "credencialId");
        if (version < 0) throw new IllegalArgumentException("Revisión inválida");
    }
}
