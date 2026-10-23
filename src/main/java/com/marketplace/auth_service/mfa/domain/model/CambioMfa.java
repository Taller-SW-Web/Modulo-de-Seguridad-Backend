package com.marketplace.auth_service.mfa.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Operación independiente del desafío de login; solo contiene el hash del token. */
public record CambioMfa(UUID id, UUID usuarioId, UUID familiaId, boolean habilitar, String hashToken,
        Instant creadoEn, Instant expiraEn, boolean consumido, Instant invalidadoEn, UUID otpActualId) {
    public boolean vigente(Instant ahora) {
        return !consumido && invalidadoEn == null && ahora.isBefore(expiraEn);
    }
    public CambioMfa consumir() {
        return new CambioMfa(id, usuarioId, familiaId, habilitar, hashToken, creadoEn, expiraEn, true, invalidadoEn, otpActualId);
    }
    public CambioMfa conCodigo(UUID codigoId) {
        return new CambioMfa(id, usuarioId, familiaId, habilitar, hashToken, creadoEn, expiraEn,
                consumido, invalidadoEn, codigoId);
    }
    @Override public String toString() { return "CambioMfa[id=" + id + ", token=REDACTADO]"; }
}
