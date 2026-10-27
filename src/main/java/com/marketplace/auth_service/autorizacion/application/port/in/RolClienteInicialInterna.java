package com.marketplace.auth_service.autorizacion.application.port.in;

import java.util.UUID;

/**
 * Rol {@code CLIENTE} de una cuenta creada por el registro público (SPEC-01 RF-01.1).
 * Solo asigna ese rol: el registro nunca concede roles privilegiados. No pasa por la
 * gestión de roles porque una cuenta recién creada no tiene actor, sesiones que revocar
 * ni evento de rol que publicar. Requiere la transacción del llamador.
 */
public interface RolClienteInicialInterna {
    void asignar(UUID usuarioId);
}
