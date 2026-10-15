package com.marketplace.auth_service.sesion.adapter.in.web.dto;

/** Cuerpo de POST {@code /api/v1/auth/login}: correo y contraseña. */
public record IniciarSesionRequest(String correo, String contrasena) {
}
