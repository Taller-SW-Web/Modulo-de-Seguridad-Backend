package com.marketplace.auth_service.identidad.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Proyección de usuario para un módulo consumidor (SPEC-18 RF-18.1/RF-18.4).
 * {@code documento} es {@code null} si el cliente no tiene uno registrado; si
 * lo tiene, viene enmascarado salvo que {@code documentoEnClaro} sea verdadero.
 */
public record UsuarioServicioVista(
        UUID id, String nombreCompleto, String correo, String telefono, String estado,
        List<String> roles, String documento, boolean documentoEnClaro) {

    public UsuarioServicioVista {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(estado, "estado");
        roles = List.copyOf(roles);
    }
}
