package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;

/** Desafío MFA creado por Luis (SPEC-09): sin familia, acceso ni reset de racha. */
public record DesafioCreado(String challengeToken, String canal, long expiraEnSegundos) {
    public DesafioCreado {
        Objects.requireNonNull(challengeToken, "challengeToken");
        Objects.requireNonNull(canal, "canal");
    }

    public DesafioMfaRespuesta comoRespuesta() {
        return new DesafioMfaRespuesta(true, challengeToken, canal, expiraEnSegundos);
    }

    @Override
    public String toString() {
        return "DesafioCreado[canal=" + canal + ", expiraEnSegundos=" + expiraEnSegundos + "]";
    }
}
