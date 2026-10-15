package com.marketplace.auth_service.identidad.domain.exception;

/**
 * Token de <b>servicio</b> bien firmado presentado en una operación que actúa
 * en nombre de una persona: {@code 403 TOKEN_NO_APLICABLE} (catálogo de
 * errores, SPEC-17). Un scope de módulo nunca se convierte en rol ni permiso.
 */
public class TokenNoAplicableException extends RuntimeException {

    public TokenNoAplicableException() {
        super("Este endpoint requiere un token de usuario");
    }
}
