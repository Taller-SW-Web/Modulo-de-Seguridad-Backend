package com.marketplace.auth_service.proteccion.application.dto;
import java.util.Objects;
import java.util.UUID;
/** Proyección propia desde CuentasPort de Eva; no es entidad ni repositorio competidor. */
public record CuentaSnapshot(UUID usuarioId, String correo, String estadoBase, int bloqueosSeguidos, int intentosFallidos) {
    public CuentaSnapshot { Objects.requireNonNull(usuarioId); Objects.requireNonNull(correo);
        Objects.requireNonNull(estadoBase);
        if (bloqueosSeguidos < 0 || intentosFallidos < 0) throw new IllegalArgumentException("Contadores inválidos"); }
    @Override public String toString() { return "CuentaSnapshot[usuarioId=" + usuarioId + "]"; }
}
