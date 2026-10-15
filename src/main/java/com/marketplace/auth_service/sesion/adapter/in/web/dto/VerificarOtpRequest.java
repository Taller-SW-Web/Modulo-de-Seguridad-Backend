package com.marketplace.auth_service.sesion.adapter.in.web.dto;

/** Cuerpo de {@code POST /auth/otp/verificar}; nunca se registra. */
public record VerificarOtpRequest(String challengeToken, String codigo) {
    @Override
    public String toString() {
        return "VerificarOtpRequest[REDACTADO]";
    }
}
