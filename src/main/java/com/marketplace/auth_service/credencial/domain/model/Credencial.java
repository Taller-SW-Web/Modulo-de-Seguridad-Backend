package com.marketplace.auth_service.credencial.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Credencial {
    private final UUID id;
    private final UUID usuarioId;
    private final HashContrasena hashActual;
    private final Instant actualizadoEn;
    private final long version;

    public Credencial(
            UUID id,
            UUID usuarioId,
            HashContrasena hashActual,
            Instant actualizadoEn,
            long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.hashActual = Objects.requireNonNull(hashActual, "hashActual");
        this.actualizadoEn = Objects.requireNonNull(actualizadoEn, "actualizadoEn");
        if (version < 0) {
            throw new IllegalArgumentException("La versión no puede ser negativa");
        }
        this.version = version;
    }

    public UUID id() {
        return id;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public HashContrasena hashActual() {
        return hashActual;
    }

    public Instant actualizadoEn() {
        return actualizadoEn;
    }

    public long version() {
        return version;
    }

    @Override
    public String toString() {
        return "Credencial[id=" + id + ", usuarioId=" + usuarioId + ", REDACTADO]";
    }
}
