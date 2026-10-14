package com.marketplace.auth_service.autorizacion.domain.exception;

/**
 * El código no pertenece al catálogo cerrado de seis roles (RF-11.1, ESC-11.2).
 * El adaptador HTTP lo traduce a {@code 400 VALIDACION}.
 */
public class RolInvalidoException extends RuntimeException {

    public RolInvalidoException(String codigo) {
        super("El código de rol no pertenece al catálogo cerrado: " + codigo);
    }
}
