package com.marketplace.auth_service.sesion.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Raíz estable de sesión (SPEC-06, DAT-01). La raíz no caduca ni se elimina al
 * rotar un token: revocar marca la raíz y los tokens de esa familia, y una
 * familia revocada nunca vuelve a activa.
 */
public record SesionFamilia(UUID id, UUID usuarioId, boolean revocada, Instant creadoEn, Instant revocadaEn) {
    public SesionFamilia {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(creadoEn, "creadoEn");
        if (revocada && revocadaEn == null) {
            throw new IllegalArgumentException("familia revocada exige revocadaEn");
        }
        if (!revocada && revocadaEn != null) {
            throw new IllegalArgumentException("familia activa no tiene revocadaEn");
        }
        if (revocadaEn != null && revocadaEn.isBefore(creadoEn)) {
            throw new IllegalArgumentException("revocadaEn no es anterior a creadoEn");
        }
    }

    public static SesionFamilia nueva(UUID id, UUID usuarioId, Instant ahora) {
        Objects.requireNonNull(ahora, "ahora");
        return new SesionFamilia(id, usuarioId, false, ahora, null);
    }

    /** Revocar confirma antes del 401 de reúso; nunca reactiva. */
    public SesionFamilia revocar(Instant ahora) {
        Objects.requireNonNull(ahora, "ahora");
        if (revocada) {
            return this;
        }
        return new SesionFamilia(id, usuarioId, true, creadoEn, ahora);
    }

    @Override
    public String toString() {
        return "SesionFamilia[id=" + id + ", revocada=" + revocada + "]";
    }
}
