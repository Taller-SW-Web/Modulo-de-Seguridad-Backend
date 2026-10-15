package com.marketplace.auth_service.auditoria.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Hecho a registrar, con códigos en texto (ACUERDOS-INTERNOS §2).
 * El adaptador valida catálogo y detalle seguro antes de persistir;
 * un token se referencia solo por su jti (SPEC-12 RF-12.5).
 */
public record RegistroSeguro(UUID registroId, String accion, String resultado,
                             String actorTipo, String actorId, UUID objetivoUsuarioId,
                             Instant fecha, String ip, String agente,
                             Map<String, String> detalle) {
    public RegistroSeguro {
        Objects.requireNonNull(registroId, "registroId");
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(actorTipo, "actorTipo");
        Objects.requireNonNull(fecha, "fecha");
        detalle = detalle == null ? Map.of() : Map.copyOf(detalle);
    }
}
