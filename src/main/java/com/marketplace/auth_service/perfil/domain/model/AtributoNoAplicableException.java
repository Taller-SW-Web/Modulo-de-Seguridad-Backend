package com.marketplace.auth_service.perfil.domain.model;

/** Un atributo de rol enviado a una cuenta sin ese rol: 422 {@code ATRIBUTO_NO_APLICABLE}. Sin valores. */
public class AtributoNoAplicableException extends RuntimeException {
    private final String campo;
    private final String rolRequerido;

    public AtributoNoAplicableException(String campo, String rolRequerido) {
        super(campo + " no aplica a una cuenta sin rol " + rolRequerido + ".");
        this.campo = campo;
        this.rolRequerido = rolRequerido;
    }

    public String campo() {
        return campo;
    }

    public String rolRequerido() {
        return rolRequerido;
    }
}
