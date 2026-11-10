package com.marketplace.auth_service.sesion.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Usuario de la sesión iniciada, para la respuesta contractual (sin secreto).
 * {@code permisos} son los efectivos del acceso emitido (FEAT-16): así el
 * cliente no necesita leer el token.
 */
public record UsuarioAutenticado(UUID id, String nombreCompleto, String correo, List<String> roles,
                                 List<String> permisos) {
    public UsuarioAutenticado {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nombreCompleto, "nombreCompleto");
        Objects.requireNonNull(correo, "correo");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        permisos = List.copyOf(Objects.requireNonNull(permisos, "permisos"));
    }

    /** Sin permisos conocidos (pruebas y llamadores previos a FEAT-16). */
    public UsuarioAutenticado(UUID id, String nombreCompleto, String correo, List<String> roles) {
        this(id, nombreCompleto, correo, roles, List.of());
    }
}
