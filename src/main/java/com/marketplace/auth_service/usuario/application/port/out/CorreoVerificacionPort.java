package com.marketplace.auth_service.usuario.application.port.out;

import java.util.UUID;

/**
 * Verificación del correo de una cuenta (SPEC-02 RF-02.1): emite el token de un solo uso y
 * encola el correo con el enlace. Emitir uno nuevo invalida el anterior. Requiere la
 * transacción del llamador, así que el correo solo sale si el hecho se confirma.
 */
public interface CorreoVerificacionPort {
    /** @throws com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException sin outbox */
    void enviar(UUID usuarioId, String correo, String nombres);
}
