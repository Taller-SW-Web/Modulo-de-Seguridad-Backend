package com.marketplace.auth_service.autorizacion.domain.exception;

import java.util.UUID;

/**
 * La cuenta objetivo no existe. El adaptador HTTP responde {@code 404 NO_ENCONTRADO};
 * solo se llega aquí después de comprobar el permiso del actor.
 */
public class UsuarioNoEncontradoException extends RuntimeException {

    public UsuarioNoEncontradoException(UUID usuarioId) {
        super("No existe la cuenta " + usuarioId);
    }
}
