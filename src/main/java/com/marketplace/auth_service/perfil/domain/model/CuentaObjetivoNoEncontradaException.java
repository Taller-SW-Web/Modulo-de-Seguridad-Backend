package com.marketplace.auth_service.perfil.domain.model;

import java.util.UUID;

/** La cuenta que el administrador quiere editar no existe: 404 {@code NO_ENCONTRADO}. */
public class CuentaObjetivoNoEncontradaException extends RuntimeException {
    public CuentaObjetivoNoEncontradaException(UUID usuarioId) {
        super("Cuenta no encontrada: " + usuarioId);
    }
}
