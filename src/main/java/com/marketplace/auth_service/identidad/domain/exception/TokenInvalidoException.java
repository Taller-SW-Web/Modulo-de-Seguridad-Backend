package com.marketplace.auth_service.identidad.domain.exception;

/**
 * El token no sirve como acceso: falta, está mal formado, la firma no valida,
 * el {@code kid} o el issuer no son los nuestros, venció o su perfil no es de
 * persona. Siempre se responde igual, {@code 401 TOKEN_INVALIDO}: el motivo
 * concreto solo queda en el servidor y nunca incluye el token.
 */
public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException(String motivo) {
        super(motivo);
    }
}
