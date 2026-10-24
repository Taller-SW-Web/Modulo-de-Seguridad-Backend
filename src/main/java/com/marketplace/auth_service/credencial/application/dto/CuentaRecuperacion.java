package com.marketplace.auth_service.credencial.application.dto;

import java.util.Objects;
import java.util.UUID;

/** Proyección mínima para emitir una recuperación sin exponer el snapshot de usuario. */
public record CuentaRecuperacion(UUID usuarioId, String correo, boolean elegible) {
    public CuentaRecuperacion {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(correo, "correo");
    }

    @Override public String toString() {
        return "CuentaRecuperacion[usuarioId=" + usuarioId + ", correo=REDACTADO, elegible=" + elegible + "]";
    }
}
