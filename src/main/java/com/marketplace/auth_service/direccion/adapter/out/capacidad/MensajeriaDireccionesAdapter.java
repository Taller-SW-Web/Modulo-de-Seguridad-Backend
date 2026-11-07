package com.marketplace.auth_service.direccion.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.direccion.application.port.out.EventosDireccionesPort;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Puente a la capacidad exportada de mensajería ({@link MensajeriaPort}). Guarda el evento en el outbox
 * dentro de la transacción del alta; lleva el nombre del campo, nunca los valores de la dirección.
 */
public final class MensajeriaDireccionesAdapter implements EventosDireccionesPort {
    private final MensajeriaPort mensajeria;

    public MensajeriaDireccionesAdapter(MensajeriaPort mensajeria) {
        this.mensajeria = Objects.requireNonNull(mensajeria);
    }

    @Override
    public void direccionesActualizadas(UUID usuarioId) {
        mensajeria.guardarEvento(new EventoUsuario(UUID.randomUUID(), "usuario.atributos_actualizados", Map.of(
                "usuarioId", usuarioId.toString(), "campos", "direcciones", "fecha", Instant.now().toString())));
    }
}
