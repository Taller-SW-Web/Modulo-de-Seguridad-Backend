package com.marketplace.auth_service.sesion.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Refresco opaco persistido (SPEC-06 RF-06.1/06.3). El secreto viaja una vez y
 * solo se almacena su SHA-256 hexadecimal; nunca el token completo, tampoco en
 * logs o excepciones. Vigencia 7 días desde su emisión, sin límite absoluto de
 * familia. Los usados se retienen mientras exista un sucesor utilizable para
 * detectar reúso.
 */
public record TokenRefresco(UUID jti, UUID usuarioId, UUID familiaId, String hashToken,
                            boolean revocado, boolean usado, Instant expiracion, Instant creadoEn) {
    public static final Duration VIGENCIA = Duration.ofDays(7);

    public TokenRefresco {
        Objects.requireNonNull(jti, "jti");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(familiaId, "familiaId");
        Objects.requireNonNull(expiracion, "expiracion");
        Objects.requireNonNull(creadoEn, "creadoEn");
        if (hashToken == null || !hashToken.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("hashToken debe ser SHA-256 hexadecimal");
        }
        if (!expiracion.isAfter(creadoEn)) {
            throw new IllegalArgumentException("expiracion debe ser posterior a creadoEn");
        }
    }

    public static TokenRefresco nuevo(UUID jti, UUID usuarioId, UUID familiaId, String hashToken, Instant ahora) {
        Objects.requireNonNull(ahora, "ahora");
        return new TokenRefresco(jti, usuarioId, familiaId, hashToken, false, false, ahora.plus(VIGENCIA), ahora);
    }

    /** Rotación RF-06.1: el usado ya no emite; el sucesor lo crea el caso de uso. */
    public TokenRefresco marcarUsado() {
        return new TokenRefresco(jti, usuarioId, familiaId, hashToken, revocado, true, expiracion, creadoEn);
    }

    public TokenRefresco revocar() {
        return new TokenRefresco(jti, usuarioId, familiaId, hashToken, true, usado, expiracion, creadoEn);
    }

    public boolean vigenteEn(Instant ahora) {
        Objects.requireNonNull(ahora, "ahora");
        return !revocado && !usado && !ahora.isBefore(creadoEn) && ahora.isBefore(expiracion);
    }

    @Override
    public String toString() {
        return "TokenRefresco[jti=" + jti + ", revocado=" + revocado + ", usado=" + usado + "]";
    }
}
