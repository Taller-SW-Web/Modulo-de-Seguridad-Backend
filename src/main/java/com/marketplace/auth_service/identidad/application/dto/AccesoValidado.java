package com.marketplace.auth_service.identidad.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Acceso de persona válido: firma, issuer, vigencia y perfil comprobados.
 * El filtro de acceso lo convierte en {@code ActorUsuario}; {@code jti} permite a
 * sesiones (EP-02) resolver la familia del acceso.
 */
public record AccesoValidado(UUID sub, UUID jti, List<String> roles, List<String> permisos, Instant expiraEn) {

    public AccesoValidado {
        roles = List.copyOf(roles);
        permisos = List.copyOf(permisos);
    }
}
