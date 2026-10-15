package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Lo que el login necesita de la cuenta (Eva). {@code disponible} = estado base ACTIVO
 * y correo verificado; el bloqueo lo decide protección (Luis), no este dato.
 */
public record CuentaLogin(UUID id, String correo, String nombreCompleto, boolean disponible, boolean mfaVoluntario) {
    public CuentaLogin {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(correo, "correo");
    }

    @Override
    public String toString() {
        return "CuentaLogin[id=" + id + "]";
    }
}
