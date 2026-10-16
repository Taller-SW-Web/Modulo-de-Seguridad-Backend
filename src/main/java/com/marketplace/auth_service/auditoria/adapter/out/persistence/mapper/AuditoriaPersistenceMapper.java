package com.marketplace.auth_service.auditoria.adapter.out.persistence.mapper;

import com.marketplace.auth_service.auditoria.adapter.out.persistence.entity.AuditoriaSeguridadJpaEntity;
import com.marketplace.auth_service.auditoria.domain.exception.DetalleInseguroException;
import com.marketplace.auth_service.auditoria.domain.model.AccionAuditable;
import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    /**
     * Fila → modelo. Las filas históricas (anteriores al catálogo cerrado o con un
     * detalle que hoy se rechazaría) no se pueden proyectar como hecho válido:
     * devuelve vacío para que la consulta (EP-06-T004) las omita en vez de fallar
     * la página entera. Nunca se modifican: la tabla es de solo anexado.
     */
    public static Optional<RegistroAuditoria> aDominio(AuditoriaSeguridadJpaEntity entidad) {
        Map<String, String> detalle = entidad.getDetalle() == null ? Map.of() : entidad.getDetalle();
        try {
            return Optional.of(new RegistroAuditoria(entidad.getRegistroId(),
                    AccionAuditable.valueOf(entidad.getAccion()),
                    entidad.getResultado(), entidad.getActorTipo(),
                    entidad.getActorId(), entidad.getObjetivoUsuarioId(),
                    Instant.from(entidad.getFecha()), entidad.getIp(), entidad.getAgente(), detalle));
        } catch (IllegalArgumentException | DetalleInseguroException historicoNoProyectable) {
            return Optional.empty();
        }
    }
}
