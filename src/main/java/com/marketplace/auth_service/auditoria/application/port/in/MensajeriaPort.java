package com.marketplace.auth_service.auditoria.application.port.in;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import java.util.UUID;

/**
 * Capacidad exportada de mensajería (TEC-INFRA-T003, catálogo de eventos).
 * Persiste el mensaje en la misma transacción del productor (outbox);
 * el worker envía fuera de locks con confirmación y reintentos.
 * Solo expone DTO propios y tipos Java.
 */
public interface MensajeriaPort {

    /**
     * Guarda un evento público para el exchange {@code seguridad.usuarios}.
     *
     * @return identificador outbox de la reserva
     */
    UUID guardarEvento(EventoUsuario evento);

    /**
     * Guarda un correo para envío asíncrono (destino y contenido se cifran).
     *
     * @return identificador outbox de la reserva
     */
    UUID guardarCorreo(EntregaCorreo correo);
}
