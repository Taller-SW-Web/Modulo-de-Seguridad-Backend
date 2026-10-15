package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;

/** Desafío MFA (OpenAPI `DesafioMfa`): todavía no hay tokens. */
public record DesafioMfaRespuesta(boolean mfaRequerido, String challengeToken, String canal, long expiraEn) {
    public DesafioMfaRespuesta {
        if (!mfaRequerido) {
            throw new IllegalArgumentException("mfaRequerido siempre es true en un desafío");
        }
        Objects.requireNonNull(challengeToken, "challengeToken");
        Objects.requireNonNull(canal, "canal");
    }

    @Override
    public String toString() {
        return "DesafioMfaRespuesta[canal=" + canal + ", expiraEn=" + expiraEn + "]";
    }
}
