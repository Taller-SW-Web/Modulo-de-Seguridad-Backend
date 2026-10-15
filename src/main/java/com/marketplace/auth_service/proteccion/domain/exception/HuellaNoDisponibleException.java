package com.marketplace.auth_service.proteccion.domain.exception;

public class HuellaNoDisponibleException extends IllegalStateException {
    public HuellaNoDisponibleException() { super("La generación de huellas no está disponible"); }
}
