package com.marketplace.auth_service.sesion.domain.exception;

/**
 * El cuerpo no trae el refresco (ausente o en blanco). El adaptador HTTP lo
 * traduce a {@code 400 VALIDACION}, como el resto del contrato.
 */
public class SecretoInvalidoException extends RuntimeException {

    public SecretoInvalidoException() {
        super("Es obligatorio.");
    }
}
