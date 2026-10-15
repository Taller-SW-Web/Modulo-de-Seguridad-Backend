package com.marketplace.auth_service.perfil.domain.model;

/**
 * Campo inválido. El mensaje nombra el campo y el motivo, nunca el valor recibido.
 * El motivo se expone aparte para que la capa web arme {@code errores[].mensaje}
 * sin repetir el nombre del campo.
 */
public class AtributoInvalidoException extends RuntimeException {
    private final String campo;
    private final String motivo;

    public AtributoInvalidoException(String campo, String motivo) {
        super(campo + ": " + motivo);
        this.campo = campo;
        this.motivo = motivo;
    }

    public String campo() {
        return campo;
    }

    public String motivo() {
        return motivo;
    }
}
