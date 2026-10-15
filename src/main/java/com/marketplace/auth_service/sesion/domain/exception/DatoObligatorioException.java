package com.marketplace.auth_service.sesion.domain.exception;

/**
 * Falta un dato obligatorio del cuerpo (correo/contrasena). El adaptador HTTP
 * lo traduce a {@code 400 VALIDACION} con el campo correspondiente.
 */
public class DatoObligatorioException extends RuntimeException {

    private final String campo;

    public DatoObligatorioException(String campo) {
        super("Es obligatorio.");
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
