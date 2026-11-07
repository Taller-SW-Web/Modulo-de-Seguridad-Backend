package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.perfil.application.port.out.EventosPerfilPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Puente a {@link MensajeriaPort}: guarda {@code usuario.atributos_actualizados} en el outbox dentro de la
 * transacción de la edición. La carga lleva los nombres de campos separados por coma, nunca sus valores.
 */
public final class EventosPerfilAdapter implements EventosPerfilPort {
    private final MensajeriaPort mensajeria;

    public EventosPerfilAdapter(MensajeriaPort mensajeria) {
        this.mensajeria = Objects.requireNonNull(mensajeria);
    }

    @Override
    public void atributosActualizados(UUID usuarioId, List<String> campos) {
        mensajeria.guardarEvento(new EventoUsuario(UUID.randomUUID(), "usuario.atributos_actualizados", Map.of(
                "usuarioId", usuarioId.toString(), "campos", String.join(",", campos),
                "fecha", Instant.now().toString())));
    }
}
