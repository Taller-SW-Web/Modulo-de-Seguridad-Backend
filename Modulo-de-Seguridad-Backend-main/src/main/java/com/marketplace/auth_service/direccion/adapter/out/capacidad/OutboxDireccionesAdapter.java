package com.marketplace.auth_service.direccion.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import com.marketplace.auth_service.direccion.application.port.out.EventosDireccionesPort;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Puente al outbox de auditoría/mensajería. */
public final class OutboxDireccionesAdapter implements EventosDireccionesPort {
    private final OutboxPort outbox;
    public OutboxDireccionesAdapter(OutboxPort outbox) { this.outbox = outbox; }
    @Override public void direccionesActualizadas(UUID usuarioId) {
        UUID eventoId = UUID.randomUUID();
        outbox.guardar(new OutboxPort.Mensaje(eventoId, "usuario.atributos_actualizados", Map.of(
                "eventoId", eventoId.toString(), "usuarioId", usuarioId.toString(),
                "campos", "direcciones", "fecha", Instant.now().toString()), 0));
    }
}
