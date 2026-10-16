package com.marketplace.auth_service.usuario.domain.exception;

import java.util.UUID;

/** La cuenta pedida no existe: 404 {@code NO_ENCONTRADO}, solo después de autorizar. */
public class CuentaNoEncontradaException extends RuntimeException {

    public CuentaNoEncontradaException(UUID id) {
        super("No existe la cuenta " + id);
    }
}
