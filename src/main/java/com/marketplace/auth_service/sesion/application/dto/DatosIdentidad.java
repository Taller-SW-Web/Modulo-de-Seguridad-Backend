package com.marketplace.auth_service.sesion.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que sesiones necesita de la identidad para renovar (SPEC-06): datos para
 * firmar el nuevo acceso y si la cuenta admite renovar. Lo resuelven Eva
 * (cuenta/estado) y Luis (protección) con reglas de Sergio (roles); sesiones
 * no consulta sus tablas.
 */
public record DatosIdentidad(UUID usuarioId, String email, String nombreCompleto,
                             List<String> roles, List<String> permisos,
                             boolean renovacionPermitida) {
    public DatosIdentidad {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(nombreCompleto, "nombreCompleto");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        permisos = List.copyOf(Objects.requireNonNull(permisos, "permisos"));
    }
}
