package com.marketplace.auth_service.usuario.domain.exception;

/** No se pudo encolar el correo de verificación (cola de correo caída): 503 {@code NO_DISPONIBLE}. */
public class VerificacionNoDisponibleException extends RuntimeException {

    public VerificacionNoDisponibleException(Throwable causa) {
        super("Correo de verificación no disponible", causa);
    }
}
