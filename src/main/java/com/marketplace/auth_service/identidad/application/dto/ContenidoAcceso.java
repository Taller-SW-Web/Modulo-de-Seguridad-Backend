package com.marketplace.auth_service.identidad.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Claims completos de un acceso de persona, tal como se firman: perfil del
 * contrato (componente tokenUsuario), sin {@code aud} ni campos añadidos.
 */
public record ContenidoAcceso(
        String issuer,
        UUID sub,
        String email,
        List<String> roles,
        List<String> permisos,
        Instant iat,
        Instant exp,
        UUID jti) {

    public static final String TIPO = "acceso";
}
