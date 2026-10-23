package com.marketplace.auth_service.autorizacion.domain.exception;

/**
 * El código no pertenece al catálogo cerrado de seis roles (RF-11.1, ESC-11.2).
 * El adaptador HTTP lo traduce a {@code 400 VALIDACION}.
 */
public class RolInvalidoException extends RuntimeException {

    private final String codigo;

    public RolInvalidoException(String codigo) {
        super("El código de rol no pertenece al catálogo cerrado: " + codigo);
        this.codigo = codigo;
    }

    /** El código recibido; {@code null} si no llegó ninguno. */
    public String codigo() {
        return codigo;
    }
}
