package com.marketplace.auth_service.sesion.adapter.in.web.dto;

/**
 * Cuerpo de POST {@code /api/v1/auth/refresh} (y del cierre): el refresco opaco
 * vigente. {@code formatoAcceso} es opcional (FEAT-16): en la renovación,
 * {@code CIFRADO} pide el acceso cifrado; el cierre lo ignora.
 */
public record RenovarSesionRequest(String refreshToken, String formatoAcceso) {
}
