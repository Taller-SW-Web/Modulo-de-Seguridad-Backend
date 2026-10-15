package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Revisión opaca de credencial (Juan José): identifica id/versión sin entregar
 * hash ni contraseña. Solo su dueño la interpreta; aquí solo se revalida.
 */
public record RevisionCredencial(UUID id, long version) {
    public RevisionCredencial {
        Objects.requireNonNull(id, "id");
        if (version < 0) {
            throw new IllegalArgumentException("versión no negativa");
        }
    }
}
