package com.marketplace.auth_service.sesion.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Datos que sesiones entrega a la firma de Sergio (SPEC-11 ya resueltos). */
public record DatosAcceso(UUID usuarioId, String email, List<String> roles, List<String> permisos) {
    public DatosAcceso {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(email, "email");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        permisos = List.copyOf(Objects.requireNonNull(permisos, "permisos"));
    }
}
