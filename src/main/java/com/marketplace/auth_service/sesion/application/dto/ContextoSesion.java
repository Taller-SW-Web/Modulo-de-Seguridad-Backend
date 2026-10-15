package com.marketplace.auth_service.sesion.application.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sesión del acceso validado (ACUERDOS-INTERNOS §2, tipo común). Solo procede
 * de la resolución interna por jti; ningún cuerpo HTTP decide actor, permiso
 * o familia.
 */
public record ContextoSesion(UUID usuarioId, UUID familiaId, UUID accessJti, Instant expiraEn) {
    public ContextoSesion {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(familiaId, "familiaId");
        Objects.requireNonNull(accessJti, "accessJti");
        Objects.requireNonNull(expiraEn, "expiraEn");
    }
}
