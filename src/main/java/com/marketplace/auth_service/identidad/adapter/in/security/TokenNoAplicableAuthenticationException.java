package com.marketplace.auth_service.identidad.adapter.in.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Token de servicio en una operación de persona: la respuesta es 403 TOKEN_NO_APLICABLE, no 401.
 * Conserva el {@code client_id} y el {@code jti} verificados para auditar el intento (ESC-17.4).
 */
public class TokenNoAplicableAuthenticationException extends AuthenticationException {

    private final String clientId;
    private final String jti;

    public TokenNoAplicableAuthenticationException() {
        this(null, null);
    }

    public TokenNoAplicableAuthenticationException(String clientId, String jti) {
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
