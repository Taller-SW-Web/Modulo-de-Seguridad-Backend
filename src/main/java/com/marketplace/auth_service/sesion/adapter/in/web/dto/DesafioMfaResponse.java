package com.marketplace.auth_service.sesion.adapter.in.web.dto;

/** Respuesta contractual `DesafioMfa`: la cuenta tiene segundo factor activo. */
public record DesafioMfaResponse(boolean mfaRequerido, String challengeToken, String canal, long expiraEn) {
}
