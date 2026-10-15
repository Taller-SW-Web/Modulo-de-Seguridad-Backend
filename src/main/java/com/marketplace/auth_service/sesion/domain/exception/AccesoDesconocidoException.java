package com.marketplace.auth_service.sesion.domain.exception;

import java.util.UUID;

/**
 * El jti no tiene asociación persistida, no corresponde al sub o ya venció.
 * El adaptador HTTP lo traduce a {@code 401} sin distinguir la causa.
 */
public class AccesoDesconocidoException extends RuntimeException {

    private final UUID jti;

    public AccesoDesconocidoException(UUID jti) {
        super("Acceso no reconocido");
        this.jti = jti;
    }

    public UUID jti() {
        return jti;
    }
}
