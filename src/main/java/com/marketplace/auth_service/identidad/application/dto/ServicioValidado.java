package com.marketplace.auth_service.identidad.application.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Token de servicio válido para esta API: firma, issuer, vigencia, perfil
 * {@code tipo=servicio} y audiencia {@code api-seguridad} comprobados.
 */
public record ServicioValidado(String clientId, UUID jti, Set<String> scopes, Instant expiraEn) {

    public ServicioValidado {
        scopes = Set.copyOf(scopes);
    }
}
