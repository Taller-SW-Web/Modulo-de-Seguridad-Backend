package com.marketplace.auth_service.sesion.adapter.in.web.dto;

/** Cuerpo de {@code POST /auth/login}. La contraseña nunca se registra. */
public record LoginRequest(String correo, String contrasena) {
    @Override
    public String toString() {
        return "LoginRequest[REDACTADO]";
    }
}
