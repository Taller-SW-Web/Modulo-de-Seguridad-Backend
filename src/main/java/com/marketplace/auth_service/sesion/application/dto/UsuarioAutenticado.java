package com.marketplace.auth_service.sesion.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Usuario de la sesión iniciada, para la respuesta contractual (sin secreto). */
public record UsuarioAutenticado(UUID id, String nombreCompleto, String correo, List<String> roles) {
    public UsuarioAutenticado {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nombreCompleto, "nombreCompleto");
        Objects.requireNonNull(correo, "correo");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
    }
}
