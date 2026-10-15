package com.marketplace.auth_service.mfa.application.dto;

import java.util.Objects;

/** Respuesta de aplicación para el mapper del login, nunca un token de acceso. */
public record DesafioLogin(String challengeToken, CanalMfa canal, int expiraEn) {
    public DesafioLogin {
        if (challengeToken == null || challengeToken.isBlank()) {
            throw new IllegalArgumentException("Desafío vacío");
        }
        Objects.requireNonNull(canal);
        if (expiraEn <= 0) throw new IllegalArgumentException("Vigencia de desafío inválida");
    }
    public boolean mfaRequerido() { return true; }
    @Override public String toString() {
        return "DesafioLogin[challengeToken=REDACTADO, canal=" + canal + ", expiraEn=" + expiraEn + "]";
    }
}
