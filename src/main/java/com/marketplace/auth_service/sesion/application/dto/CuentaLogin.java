package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;
import java.util.UUID;

/** Cuenta para el login, resuelta y bloqueada por Eva. Sin credencial. */
public record CuentaLogin(UUID id, String nombreCompleto, String correo, EstadoCuentaLogin estado,
                          boolean mfaHabilitado) {
    public CuentaLogin {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nombreCompleto, "nombreCompleto");
        Objects.requireNonNull(correo, "correo");
        Objects.requireNonNull(estado, "estado");
    }
}
