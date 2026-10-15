package com.marketplace.auth_service.auditoria.adapter.out.persistence.mapper;

import com.marketplace.auth_service.auditoria.adapter.out.persistence.entity.AuditoriaSeguridadJpaEntity;
import com.marketplace.auth_service.auditoria.domain.model.AccionAuditable;
import com.marketplace.auth_service.auditoria.domain.model.ActorTipo;
import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import com.marketplace.auth_service.auditoria.domain.model.Resultado;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/** Mapeo manual entidad ↔ modelo propio; sin secretos fuera del detalle validado. */
public final class AuditoriaPersistenceMapper {

    private AuditoriaPersistenceMapper() {
    }

    public static AuditoriaSeguridadJpaEntity aEntidad(RegistroAuditoria modelo) {
        return new AuditoriaSeguridadJpaEntity(null, modelo.registroId(),
                modelo.accion().name(), modelo.resultado(), modelo.actorTipo(),
                modelo.actorId(), modelo.objetivoUsuarioId(),
                OffsetDateTime.ofInstant(modelo.fecha(), ZoneOffset.UTC),
                modelo.ip(), modelo.agente(),
                modelo.detalle().isEmpty() ? null : new HashMap<>(modelo.detalle()));
    }

    public static RegistroAuditoria aDominio(AuditoriaSeguridadJpaEntity entidad) {
        Map<String, String> detalle = entidad.getDetalle() == null ? Map.of() : entidad.getDetalle();
        return new RegistroAuditoria(entidad.getRegistroId(), AccionAuditable.valueOf(entidad.getAccion()),
                Resultado.valueOf(entidad.getResultado().name()),
                ActorTipo.valueOf(entidad.getActorTipo().name()),
                entidad.getActorId(), entidad.getObjetivoUsuarioId(),
                Instant.from(entidad.getFecha()), entidad.getIp(), entidad.getAgente(), detalle);
    }
}
