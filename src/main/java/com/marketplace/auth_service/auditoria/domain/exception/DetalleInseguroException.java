package com.marketplace.auth_service.auditoria.domain.exception;

/**
 * El detalle contiene un secreto o dato prohibido por RF-12.5
 * (contraseña, hash, OTP, token completo o documento en claro).
 * Un token se referencia solo por su {@code jti}.
 */
public class DetalleInseguroException extends RuntimeException {

    private final String clave;

    public DetalleInseguroException(String clave) {
        super("El detalle de auditoría contiene un dato prohibido: " + clave);
        this.clave = clave;
    }

    /** La clave rechazada. */
    public String clave() {
        return clave;
    }
}
