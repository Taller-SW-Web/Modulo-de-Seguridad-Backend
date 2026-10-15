package com.marketplace.auth_service.auditoria.application.dto;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público de usuario para el exchange {@code seguridad.usuarios}
 * (catálogo de eventos). El productor decide hecho y contenido; el worker
 * publica fuera de locks con eventoId estable para deduplicar.
 */
public record EventoUsuario(UUID eventoId, String topico, Map<String, String> carga) {
    public EventoUsuario {
        Objects.requireNonNull(eventoId, "eventoId");
        Objects.requireNonNull(topico, "topico");
        carga = carga == null ? Map.of() : Map.copyOf(carga);
    }
}
