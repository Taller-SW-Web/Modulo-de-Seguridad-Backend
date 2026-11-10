package com.marketplace.auth_service.sesion.adapter.in.web.dto;

/**
 * Cuerpo de {@code POST /auth/login}. La contraseña nunca se registra.
 * {@code formatoAcceso} es opcional (FEAT-16): {@code CIFRADO} pide el acceso cifrado.
 */
public record LoginRequest(String correo, String contrasena, String formatoAcceso) {
    @Override
    public String toString() {
        return "LoginRequest[REDACTADO]";
    }
}
