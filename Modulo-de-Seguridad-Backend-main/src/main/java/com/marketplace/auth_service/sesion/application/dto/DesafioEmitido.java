package com.marketplace.auth_service.sesion.application.dto;

/** Desafío MFA ya persistido por su dueño; el token solo viaja en la respuesta HTTP. */
public record DesafioEmitido(String challengeToken, String canal, int expiraEn) {
    public DesafioEmitido {
        if (challengeToken == null || challengeToken.isBlank()) {
            throw new IllegalArgumentException("Desafío vacío");
        }
        if (canal == null || canal.isBlank()) {
            throw new IllegalArgumentException("Canal vacío");
        }
        if (expiraEn <= 0) {
            throw new IllegalArgumentException("Vigencia inválida");
        }
    }

    @Override
    public String toString() {
        return "DesafioEmitido[challengeToken=REDACTADO, canal=" + canal + ", expiraEn=" + expiraEn + "]";
    }
}
