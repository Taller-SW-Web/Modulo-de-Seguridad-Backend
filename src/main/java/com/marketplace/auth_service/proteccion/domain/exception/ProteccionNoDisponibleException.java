package com.marketplace.auth_service.proteccion.domain.exception;
public class ProteccionNoDisponibleException extends IllegalStateException {
    public ProteccionNoDisponibleException(String dependencia) { super("Protección no disponible: " + dependencia); }
}
