package com.marketplace.auth_service.sesion.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;

/** Respuesta contractual {@code SesionIniciada} (SPEC-05/06). */
public record SesionIniciadaResponse(String accessToken, String refreshToken, String tokenType,
                                     long expiresIn, UsuarioSesionResponse usuario) {
    public record UsuarioSesionResponse(UUID id, String nombreCompleto, String correo, List<String> roles) {
    }
}
