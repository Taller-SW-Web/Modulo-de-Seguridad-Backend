package com.marketplace.auth_service.direccion.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.direccion.application.port.out.AuditoriaDireccionesPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puente a {@link AuditoriaPort}: registra {@code ATRIBUTOS_ACTUALIZADOS} como acción no crítica (tras el
 * commit, con reintento privado). Un fallo de auditoría nunca revierte ni oculta la dirección ya creada.
 */
public final class AuditoriaDireccionesAdapter implements AuditoriaDireccionesPort {
    private static final Logger LOG = LoggerFactory.getLogger(AuditoriaDireccionesAdapter.class);

    private final AuditoriaPort auditoria;

    public AuditoriaDireccionesAdapter(AuditoriaPort auditoria) {
        this.auditoria = Objects.requireNonNull(auditoria);
    }

    @Override
    public void direccionAgregada(ActorUsuario actor, UUID usuarioId, ContextoPeticion contexto) {
        try {
            auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "ATRIBUTOS_ACTUALIZADOS",
                    "EXITO", "USUARIO", actor.sub().toString(), usuarioId, Instant.now(),
                    contexto == null ? null : contexto.ip(), contexto == null ? null : contexto.agente(),
                    Map.of("campos", "direcciones")));
        } catch (RuntimeException fallo) {
            // Solo la clase: el mensaje podría arrastrar datos de la petición.
            LOG.warn("No se pudo registrar ATRIBUTOS_ACTUALIZADOS de direcciones: {}", fallo.getClass().getSimpleName());
        }
    }

    @Override
    public void moduloConsultoDirecciones(String clientId, UUID usuarioId) {
        try {
            auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(),
                    "MODULO_CONSULTO_DIRECCIONES", "EXITO", "MODULO", clientId, usuarioId, Instant.now(), null, null,
                    Map.of("endpoint", "GET /api/v1/usuarios/{id}/direcciones")));
        } catch (RuntimeException fallo) {
            LOG.warn("No se pudo registrar MODULO_CONSULTO_DIRECCIONES: {}", fallo.getClass().getSimpleName());
        }
    }
}
