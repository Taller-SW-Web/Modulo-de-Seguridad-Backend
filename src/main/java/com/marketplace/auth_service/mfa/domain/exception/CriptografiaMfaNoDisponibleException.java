package com.marketplace.auth_service.mfa.domain.exception;

public class CriptografiaMfaNoDisponibleException extends RuntimeException {
    public CriptografiaMfaNoDisponibleException() {
        super("La criptografía MFA no está disponible en este entorno");
    }
}
