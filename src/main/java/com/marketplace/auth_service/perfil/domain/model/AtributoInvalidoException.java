package com.marketplace.auth_service.perfil.domain.model;

/** Campo inválido. El mensaje nombra el campo y el motivo, nunca el valor recibido. */
public class AtributoInvalidoException extends RuntimeException {
    private final String campo;

    public AtributoInvalidoException(String campo, String motivo) {
        super(campo + ": " + motivo);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
