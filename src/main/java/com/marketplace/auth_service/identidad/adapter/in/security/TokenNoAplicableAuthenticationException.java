package com.marketplace.auth_service.identidad.adapter.in.security;

import org.springframework.security.core.AuthenticationException;

/** Token de servicio en una operación de persona: la respuesta es 403 TOKEN_NO_APLICABLE, no 401. */
public class TokenNoAplicableAuthenticationException extends AuthenticationException {

    public TokenNoAplicableAuthenticationException() {
        super("Este endpoint requiere un token de usuario");
    }
}
