package com.marketplace.auth_service.usuario.domain.exception;

/**
 * La cuota de reenvíos no puede calcularse (falta o es inválida la clave HMAC): 503
 * {@code NO_DISPONIBLE}. Falla igual con cualquier correo, así que no revela cuentas.
 */
public class CuotaNoDisponibleException extends RuntimeException {

    public CuotaNoDisponibleException() {
        super("Cuota de reenvío de verificación no disponible");
    }
}
