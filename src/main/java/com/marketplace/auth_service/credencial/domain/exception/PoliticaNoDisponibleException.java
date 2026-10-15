package com.marketplace.auth_service.credencial.domain.exception;

public class PoliticaNoDisponibleException extends RuntimeException {
    public PoliticaNoDisponibleException() { super("No se puede validar la contraseña en este momento"); }
}
