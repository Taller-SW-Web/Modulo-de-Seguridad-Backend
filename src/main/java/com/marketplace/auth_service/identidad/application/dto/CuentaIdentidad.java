package com.marketplace.auth_service.identidad.application.dto;

import java.util.Objects;
import java.util.UUID;

/** Cuenta básica proyectada para SPEC-18, sin credenciales ni documento. */
public record CuentaIdentidad(UUID id, String nombres, String apellidos, String nombreCompleto, String correo,
                              String celular, String estado, boolean correoVerificado, boolean celularVerificado) {

    public CuentaIdentidad {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(estado, "estado");
    }
}
