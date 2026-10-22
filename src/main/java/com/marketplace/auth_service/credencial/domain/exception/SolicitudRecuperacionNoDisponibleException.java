package com.marketplace.auth_service.credencial.domain.exception;

/** La cuota no puede aplicarse de forma segura por falta de configuración criptográfica. */
public class SolicitudRecuperacionNoDisponibleException extends RuntimeException {
    public SolicitudRecuperacionNoDisponibleException() {
        super("No está disponible la protección de solicitudes de recuperación");
    }
}
