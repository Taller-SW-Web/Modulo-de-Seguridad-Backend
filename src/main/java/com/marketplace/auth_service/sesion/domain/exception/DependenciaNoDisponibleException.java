package com.marketplace.auth_service.sesion.domain.exception;

/**
 * Una capacidad dueña aún no entrega su puerto y el recorrido no puede
 * completarse sin inventar sus reglas. El adaptador HTTP lo traduce a
 * {@code 503 NO_DISPONIBLE}, nunca a un éxito parcial.
 */
public class DependenciaNoDisponibleException extends RuntimeException {

    public DependenciaNoDisponibleException(String capacidad) {
        super("Capacidad aún no disponible: " + capacidad);
    }
}
