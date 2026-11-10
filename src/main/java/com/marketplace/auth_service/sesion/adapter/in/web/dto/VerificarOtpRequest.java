package com.marketplace.auth_service.sesion.adapter.in.web.dto;

/**
 * Cuerpo de {@code POST /auth/otp/verificar}; nunca se registra.
 * {@code formatoAcceso} es opcional (FEAT-16): {@code CIFRADO} pide el acceso cifrado.
 */
public record VerificarOtpRequest(String challengeToken, String codigo, String formatoAcceso) {
    @Override
    public String toString() {
        return "VerificarOtpRequest[REDACTADO]";
    }
}
