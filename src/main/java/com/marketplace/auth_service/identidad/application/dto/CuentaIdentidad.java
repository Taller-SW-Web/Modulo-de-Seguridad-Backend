package com.marketplace.auth_service.identidad.application.dto;

import java.util.Objects;
import java.util.UUID;

/** Cuenta básica proyectada para SPEC-18, sin credenciales ni documento. */
public record CuentaIdentidad(UUID id, String nombreCompleto, String correo, String telefono, String estado) {

    public CuentaIdentidad {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(estado, "estado");
    }
}
