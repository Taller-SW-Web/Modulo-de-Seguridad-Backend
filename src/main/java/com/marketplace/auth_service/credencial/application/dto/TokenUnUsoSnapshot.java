package com.marketplace.auth_service.credencial.application.dto;

import java.time.Instant;
import java.util.UUID;

/** Resultado interno sin hash persistido ni token claro. */
public record TokenUnUsoSnapshot(UUID id, UUID usuarioId, PropositoToken proposito, UUID contextoId,
                                 String destino, Instant expiraEn, Instant creadoEn, boolean usado) {
    @Override public String toString() {
        return "TokenUnUsoSnapshot[id=" + id + ", usuarioId=" + usuarioId + ", proposito=" + proposito
                + ", contextoId=" + contextoId + ", destino=" + (destino == null ? "null" : "REDACTADO")
                + ", expiraEn=" + expiraEn + ", creadoEn=" + creadoEn + ", usado=" + usado + "]";
    }
}
