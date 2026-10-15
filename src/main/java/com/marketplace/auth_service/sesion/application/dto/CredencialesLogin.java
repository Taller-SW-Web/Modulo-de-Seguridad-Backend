package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;

/** Credenciales presentadas. El correo llega crudo; el servicio lo normaliza. */
public record CredencialesLogin(String correo, SecretoEfimero contrasena) {
    public CredencialesLogin {
        Objects.requireNonNull(correo, "correo");
        Objects.requireNonNull(contrasena, "contrasena");
    }
}
