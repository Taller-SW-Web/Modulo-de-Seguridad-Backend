package com.marketplace.auth_service.autorizacion.domain.exception;

import com.marketplace.auth_service.autorizacion.domain.model.Permiso;

import java.util.UUID;

/**
 * El actor no tiene el permiso que exige la operación. El adaptador HTTP lo
 * traduce a {@code 403 SCOPE_INSUFICIENTE} sin revelar qué permiso faltaba
 * (catálogo de errores); {@link #permiso()} y {@link #actor()} quedan para
 * auditar el intento ({@code ACCESO_DENEGADO}, SPEC-12).
 */
public class AccesoDenegadoException extends RuntimeException {

    private final UUID actor;
    private final Permiso permiso;

    public AccesoDenegadoException(UUID actor, Permiso permiso) {
        super("Permisos insuficientes");
        this.actor = actor;
        this.permiso = permiso;
    }

    public UUID actor() {
        return actor;
    }

    public Permiso permiso() {
        return permiso;
    }
}
