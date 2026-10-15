package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;
import java.util.UUID;

/** Revisión de credencial preverificada (id + versión); nunca hash ni contraseña. */
public record RevisionLogin(UUID usuarioId, UUID credencialId, long version) {
    public RevisionLogin {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(credencialId, "credencialId");
        if (version < 0) {
            throw new IllegalArgumentException("Revisión inválida");
        }
    }
}
