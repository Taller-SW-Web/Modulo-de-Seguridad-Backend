package com.marketplace.auth_service.identidad.domain.exception;

/**
 * Token de <b>servicio</b> bien firmado presentado en una operación que actúa
 * en nombre de una persona: {@code 403 TOKEN_NO_APLICABLE} (catálogo de
 * errores, SPEC-17). Un scope de módulo nunca se convierte en rol ni permiso.
 *
 * <p>Lleva el {@code client_id} y el {@code jti} ya verificados para auditar
 * el intento (ESC-17.4); nunca el token. Ambos faltan cuando el rechazo es el
 * inverso (acceso de persona en una ruta de servicio).
 */
public class TokenNoAplicableException extends RuntimeException {

    private final String clientId;
    private final String jti;

    public TokenNoAplicableException() {
        this(null, null);
    }

    public TokenNoAplicableException(String clientId, String jti) {
        super("Este endpoint requiere un token de usuario");
        this.clientId = clientId;
        this.jti = jti;
    }

    public String clientId() {
        return clientId;
    }

    public String jti() {
        return jti;
    }
}
