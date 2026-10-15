package com.marketplace.auth_service.credencial.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Token persistido por digest; el valor claro nunca forma parte del agregado. */
public final class TokenUnUso {
    private final UUID id;
    private final UUID usuarioId;
    private final PropositoTokenUnUso proposito;
    private final String hashToken;
    private final UUID contextoId;
    private final Instant expiraEn;
    private final Instant creadoEn;
    private final boolean usado;

    public TokenUnUso(UUID id, UUID usuarioId, PropositoTokenUnUso proposito, String hashToken,
                      UUID contextoId, Instant expiraEn, Instant creadoEn, boolean usado) {
        this.id = Objects.requireNonNull(id, "id");
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId");
        this.proposito = Objects.requireNonNull(proposito, "proposito");
        this.hashToken = Objects.requireNonNull(hashToken, "hashToken");
        if (hashToken.isBlank()) throw new IllegalArgumentException("El hash del token es obligatorio");
        if (proposito == PropositoTokenUnUso.DESBLOQUEO_AUTOMATICO && contextoId == null)
            throw new IllegalArgumentException("El desbloqueo automático requiere revisión de bloqueo");
        this.contextoId = contextoId;
        this.expiraEn = Objects.requireNonNull(expiraEn, "expiraEn");
        this.creadoEn = Objects.requireNonNull(creadoEn, "creadoEn");
        if (!expiraEn.isAfter(creadoEn)) throw new IllegalArgumentException("El token debe expirar después de su creación");
        this.usado = usado;
    }

    public UUID id() { return id; }
    public UUID usuarioId() { return usuarioId; }
    public PropositoTokenUnUso proposito() { return proposito; }
    public String hashToken() { return hashToken; }
    public UUID contextoId() { return contextoId; }
    public Instant expiraEn() { return expiraEn; }
    public Instant creadoEn() { return creadoEn; }
    public boolean usado() { return usado; }
    public boolean vigenteEn(Instant ahora) { return !usado && expiraEn.isAfter(ahora); }
    public TokenUnUso marcarUsado() {
        return new TokenUnUso(id, usuarioId, proposito, hashToken, contextoId, expiraEn, creadoEn, true);
    }

    @Override public String toString() {
        return "TokenUnUso[id=" + id + ", usuarioId=" + usuarioId + ", proposito=" + proposito + ", REDACTADO]";
    }
}
