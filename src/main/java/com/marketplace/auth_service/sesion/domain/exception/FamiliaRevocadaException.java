package com.marketplace.auth_service.sesion.domain.exception;

import java.util.UUID;

/**
 * La familia del acceso está revocada (reúso o cierre). Un JWT ya emitido se
 * sigue validando por firma y exp en el consumidor; aquí no se promete
 * invalidación inmediata de accesos remotos.
 */
public class FamiliaRevocadaException extends RuntimeException {

    private final UUID familiaId;

    public FamiliaRevocadaException(UUID familiaId) {
        super("Sesión terminada");
        this.familiaId = familiaId;
    }

    public UUID familiaId() {
        return familiaId;
    }
}
