package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.perfil.application.port.out.AuditoriaPerfilPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puente a {@link AuditoriaPort}: {@code ATRIBUTOS_ACTUALIZADOS} no crítico tras el commit, con el
 * administrador como actor y la cuenta editada como objetivo. Un fallo de auditoría no revierte la edición.
 */
public final class AuditoriaPerfilAdapter implements AuditoriaPerfilPort {
    private static final Logger LOG = LoggerFactory.getLogger(AuditoriaPerfilAdapter.class);

    private final AuditoriaPort auditoria;

    public AuditoriaPerfilAdapter(AuditoriaPort auditoria) {
        this.auditoria = Objects.requireNonNull(auditoria);
    }

    @Override
    public void atributosActualizados(ActorUsuario actor, UUID usuarioId, List<String> campos,
                                      ContextoPeticion contexto) {
        try {
            auditoria.registrarNoCriticaTrasCommit(new RegistroSeguro(UUID.randomUUID(), "ATRIBUTOS_ACTUALIZADOS",
                    "EXITO", "USUARIO", actor.sub().toString(), usuarioId, Instant.now(),
                    contexto == null ? null : contexto.ip(), contexto == null ? null : contexto.agente(),
                    Map.of("campos", String.join(",", campos), "via", "ADMINISTRACION")));
        } catch (RuntimeException fallo) {
            // Solo la clase: el mensaje podría arrastrar datos de la petición.
            LOG.warn("No se pudo registrar ATRIBUTOS_ACTUALIZADOS administrativo: {}",
                    fallo.getClass().getSimpleName());
        }
    }
}
