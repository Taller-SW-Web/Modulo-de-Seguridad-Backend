package com.marketplace.auth_service.usuario.application.port.out;

import java.util.UUID;

/**
 * Enlace de activación de una cuenta creada por otra persona (SPEC-03 RF-03.5): emite el token de un
 * solo uso de 72 horas y encola el correo. Emitir uno nuevo invalida el anterior. Requiere la
 * transacción del llamador, así que el correo solo sale si el alta se confirma.
 */
public interface CorreoActivacionPort {
    /** @throws com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException sin outbox */
    void enviar(UUID usuarioId, String correo, String nombres);
}
