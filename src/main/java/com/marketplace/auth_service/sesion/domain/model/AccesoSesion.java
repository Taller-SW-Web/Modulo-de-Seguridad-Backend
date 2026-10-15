package com.marketplace.auth_service.sesion.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Asociación jti → usuario/familia/vigencia (DAT-01). Cada acceso emitido tiene
 * jti nuevo y fila propia, incluso tras refresh; el JWT público no recibe
 * familiaId nuevo. {@code resolverAcceso} verifica jti, sub y exp contra esta
 * relación y la raíz activa; la familia nunca se infiere por sub ni la elige el
 * navegador. La fila se conserva mientras el acceso sea vigente.
 */
public record AccesoSesion(UUID jti, UUID usuarioId, UUID familiaId, Instant expiraEn, Instant creadoEn) {
    public static final Duration VIGENCIA = Duration.ofSeconds(900);

    public AccesoSesion {
        Objects.requireNonNull(jti, "jti");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(familiaId, "familiaId");
        Objects.requireNonNull(expiraEn, "expiraEn");
        Objects.requireNonNull(creadoEn, "creadoEn");
        if (!expiraEn.isAfter(creadoEn)) {
            throw new IllegalArgumentException("expiraEn debe ser posterior a creadoEn");
        }
    }

    public static AccesoSesion nuevo(UUID jti, UUID usuarioId, UUID familiaId, Instant ahora) {
        Objects.requireNonNull(ahora, "ahora");
        return new AccesoSesion(jti, usuarioId, familiaId, ahora.plus(VIGENCIA), ahora);
    }

    /** DAT-01: el acceso resuelve para este usuario y familia mientras esté vigente. */
    public boolean asociaA(UUID usuario, UUID familia, Instant ahora) {
        Objects.requireNonNull(usuario, "usuario");
        Objects.requireNonNull(familia, "familia");
        Objects.requireNonNull(ahora, "ahora");
        return usuarioId.equals(usuario) && familiaId.equals(familia)
                && !ahora.isBefore(creadoEn) && ahora.isBefore(expiraEn);
    }

    @Override
    public String toString() {
        return "AccesoSesion[jti=" + jti + "]";
    }
}
