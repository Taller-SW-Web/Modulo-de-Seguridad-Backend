package com.marketplace.auth_service.perfil.domain.model;

import java.util.UUID;

/** La cuenta pedida no existe (tras pasar la autorización). */
public class CuentaPerfilNoEncontradaException extends RuntimeException {
    public CuentaPerfilNoEncontradaException(UUID usuarioId) {
        super("Cuenta no encontrada: " + usuarioId);
    }
}
