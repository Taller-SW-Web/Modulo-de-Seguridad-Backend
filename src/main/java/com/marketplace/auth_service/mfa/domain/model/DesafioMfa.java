package com.marketplace.auth_service.mfa.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Estado persistible del desafío; nunca retiene su token en claro. */
public record DesafioMfa(UUID id, UUID usuarioId, UUID credencialId, Long credencialVersion,
                         String hashToken, Instant creadoEn, Instant expiraEn, boolean consumido) {
    public static final Duration VIGENCIA = Duration.ofSeconds(300);

    public DesafioMfa {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(creadoEn, "creadoEn");
        Objects.requireNonNull(expiraEn, "expiraEn");
        if (hashToken == null || !hashToken.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("hashToken debe ser SHA-256 hexadecimal");
        }
        if ((credencialId == null) != (credencialVersion == null)
                || (credencialVersion != null && credencialVersion < 0)) {
            throw new IllegalArgumentException("revisión de credencial incompleta o inválida");
        }
        if (!expiraEn.isAfter(creadoEn)) {
            throw new IllegalArgumentException("expiraEn debe ser posterior a creadoEn");
        }
    }

    public static DesafioMfa nuevo(UUID id, UUID usuarioId, UUID credencialId, long credencialVersion,
                                   String hashToken, Instant ahora) {
        Objects.requireNonNull(credencialId, "credencialId");
        Objects.requireNonNull(ahora, "ahora");
        return new DesafioMfa(id, usuarioId, credencialId, credencialVersion, hashToken,
                ahora, ahora.plus(VIGENCIA), false);
    }

    /** Históricos sin revisión no habilitan login; exp == ahora ya está vencido. */
    public boolean vigenteEn(Instant ahora) {
        Objects.requireNonNull(ahora, "ahora");
        return credencialId != null && !consumido && !ahora.isBefore(creadoEn) && ahora.isBefore(expiraEn);
    }

    public boolean correspondeA(UUID usuario, UUID credencial, long version) {
        return usuarioId.equals(usuario) && credencialId != null
                && credencialId.equals(credencial) && credencialVersion == version;
    }

    @Override
    public String toString() {
        return "DesafioMfa[id=" + id + ", expiraEn=" + expiraEn + ", consumido=" + consumido + "]";
    }
}
