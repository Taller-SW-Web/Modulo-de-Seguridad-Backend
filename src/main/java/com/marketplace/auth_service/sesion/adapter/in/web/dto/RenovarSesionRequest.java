package com.marketplace.auth_service.sesion.adapter.in.web.dto;

/** Cuerpo de POST {@code /api/v1/auth/refresh}: el refresco opaco vigente. */
public record RenovarSesionRequest(String refreshToken) {
}
