package com.marketplace.auth_service.perfil.domain.exception;

/** Solo el titular cambia su correo: otra cuenta, o una cuenta que ya no existe, recibe el mismo rechazo. */
public final class CambioCorreoNoPermitidaException extends RuntimeException {
    public CambioCorreoNoPermitidaException() {
        super("Cambio de correo no permitido", null, false, false);
    }
}
