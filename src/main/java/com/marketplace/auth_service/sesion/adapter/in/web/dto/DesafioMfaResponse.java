package com.marketplace.auth_service.sesion.adapter.in.web.dto;

/** Respuesta contractual {@code DesafioMfa}: todavía no hay tokens. */
public record DesafioMfaResponse(boolean mfaRequerido, String challengeToken, int expiraEn, String canal) {
    @Override
    public String toString() {
        return "DesafioMfaResponse[challengeToken=REDACTADO]";
    }
}
