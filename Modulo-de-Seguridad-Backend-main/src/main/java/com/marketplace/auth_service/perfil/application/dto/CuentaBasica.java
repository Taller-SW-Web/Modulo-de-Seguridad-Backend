package com.marketplace.auth_service.perfil.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Lo que el perfil necesita de la cuenta (capacidad de Eva). DTO propio: el puente
 * adapter.out.capacidad lo construye desde CuentaSnapshot. Sin documento, hash ni roles.
 */
public record CuentaBasica(
        UUID id,
        String nombres,
        String apellidos,
        String nombreCompleto,
        String correo,
        String celular,
        String estado,
        boolean correoVerificado,
        boolean celularVerificado) {
    public CuentaBasica {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nombreCompleto, "nombreCompleto");
        Objects.requireNonNull(correo, "correo");
        Objects.requireNonNull(estado, "estado");
    }

    @Override
    public String toString() {
        return "CuentaBasica[id=" + id + "]";
    }
}
