package com.marketplace.auth_service.identidad.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Claims completos de un token de servicio, tal como se firman (SPEC-17,
 * componente tokenServicio): {@code sub} es el {@code client_id}, {@code aud}
 * la lista de APIs dueñas de los scopes y {@code scope} el texto separado por
 * espacios (RFC 9068). Sin roles, permisos ni email: identifica a un módulo.
 */
public record ContenidoServicio(
        String issuer,
        String clientId,
        List<String> audiencias,
        List<String> scopes,
        Instant iat,
        Instant exp,
        UUID jti) {

    public static final String TIPO = "servicio";

    public ContenidoServicio {
        audiencias = List.copyOf(audiencias);
        scopes = List.copyOf(scopes);
    }
}
