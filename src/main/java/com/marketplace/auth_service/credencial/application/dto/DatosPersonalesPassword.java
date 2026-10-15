package com.marketplace.auth_service.credencial.application.dto;

import java.util.Objects;

public record DatosPersonalesPassword(String nombres, String apellidos, String correo) {
    public DatosPersonalesPassword {
        Objects.requireNonNull(nombres);
        Objects.requireNonNull(apellidos);
        Objects.requireNonNull(correo);
    }
    @Override public String toString() { return "DatosPersonalesPassword[REDACTADO]"; }
}
