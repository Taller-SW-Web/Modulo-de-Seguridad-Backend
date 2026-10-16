package com.marketplace.auth_service.sesion.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Identidad ya resuelta que el coordinador (login/MFA) entrega al abrir una
 * familia. La cuenta la resolvió Eva, la credencial Juan José y los
 * roles/permisos Sergio (SPEC-11): la apertura no recalcula nada ni depende de
 * LoginService/MfaService. Listas vacías son válidas.
 */
public record IdentidadAutorizada(UUID usuarioId, String nombreCompleto, String correo,
                                  List<String> roles, List<String> permisos) {
    public IdentidadAutorizada {
        Objects.requireNonNull(usuarioId, "usuarioId");
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            throw new IllegalArgumentException("nombreCompleto es obligatorio");
        }
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("correo es obligatorio");
        }
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        permisos = List.copyOf(Objects.requireNonNull(permisos, "permisos"));
    }
}
