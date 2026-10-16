package com.marketplace.auth_service.mfa.domain.exception;

/** No hay transporte configurado para entregar el código: 503 sin consumir cuota. */
public class EntregaOtpNoDisponibleException extends RuntimeException {
    public EntregaOtpNoDisponibleException() {
        super("La entrega de códigos no está configurada en este entorno");
    }
}
