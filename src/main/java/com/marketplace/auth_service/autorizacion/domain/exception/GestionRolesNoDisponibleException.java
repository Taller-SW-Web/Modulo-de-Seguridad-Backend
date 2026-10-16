package com.marketplace.auth_service.autorizacion.domain.exception;

/**
 * Falta una dependencia del cambio de rol: un proveedor que aún no está
 * compuesto, o una auditoría crítica u outbox que no se pudo guardar. El
 * adaptador HTTP responde {@code 503 NO_DISPONIBLE} y no queda ningún cambio.
 */
public class GestionRolesNoDisponibleException extends RuntimeException {

    public GestionRolesNoDisponibleException(String motivo) {
        super(motivo);
    }

    public GestionRolesNoDisponibleException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
