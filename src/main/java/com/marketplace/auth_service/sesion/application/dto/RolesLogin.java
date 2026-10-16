package com.marketplace.auth_service.sesion.application.dto;

import java.util.List;
import java.util.Objects;

/** Roles y permisos actuales para el token (Sergio); códigos ordenados, nunca null. */
public record RolesLogin(List<String> roles, List<String> permisos) {
    public RolesLogin {
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        permisos = List.copyOf(Objects.requireNonNull(permisos, "permisos"));
    }
}
