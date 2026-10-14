package com.marketplace.auth_service.identidad.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Identidad ya resuelta que el login (EP-02) entrega para firmar un acceso.
 * Los roles y permisos llegan calculados por SPEC-11: el firmador no los
 * calcula ni los completa. Listas vacías son válidas y se firman como arrays.
 */
public record ClaimsAcceso(UUID usuarioId, String email, List<String> roles, List<String> permisos) {

    public ClaimsAcceso {
        Objects.requireNonNull(usuarioId, "usuarioId");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email es obligatorio");
        }
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        permisos = List.copyOf(Objects.requireNonNull(permisos, "permisos"));
    }
}
