package com.marketplace.auth_service.usuario.domain.exception;

/** Un parámetro de la consulta de cuentas no cumple el contrato: 400 {@code VALIDACION}. */
public class ConsultaInvalidaException extends RuntimeException {

    private final String campo;

    public ConsultaInvalidaException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
