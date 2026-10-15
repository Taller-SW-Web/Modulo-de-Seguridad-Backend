package com.marketplace.auth_service.auditoria.adapter.out.persistence.mapper;

import com.marketplace.auth_service.auditoria.domain.model.AccionAuditable;
import com.marketplace.auth_service.auditoria.domain.model.ActorTipo;
import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import com.marketplace.auth_service.auditoria.domain.model.Resultado;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditoriaPersistenceMapperTest {
    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

    private static RegistroAuditoria modelo() {
        return new RegistroAuditoria(UUID.randomUUID(), AccionAuditable.ROL_ASIGNADO,
                Resultado.EXITO, ActorTipo.USUARIO, "sub-1", UUID.randomUUID(), AHORA,
                "10.0.0.1", "agent", Map.of("rol", "VENDEDOR"));
    }

    @Test
    void mapeaIdaConvirtiendoFechaAUtc() {
        var entidad = AuditoriaPersistenceMapper.aEntidad(modelo());
        assertThat(entidad.getId()).isNull();
        assertThat(entidad.getRegistroId()).isNotNull();
        assertThat(entidad.getAccion()).isEqualTo("ROL_ASIGNADO");
        assertThat(entidad.getFecha()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
        assertThat(entidad.getDetalle()).containsEntry("rol", "VENDEDOR");
    }

    @Test
    void mapeaVueltaExcluyendoIdNumericoDelModelo() {
        var entidad = AuditoriaPersistenceMapper.aEntidad(modelo());
        var dominio = AuditoriaPersistenceMapper.aDominio(entidad);
        assertThat(dominio.accion()).isEqualTo(AccionAuditable.ROL_ASIGNADO);
        assertThat(dominio.resultado()).isEqualTo(Resultado.EXITO);
        assertThat(dominio.actorTipo()).isEqualTo(ActorTipo.USUARIO);
        assertThat(dominio.detalle()).containsEntry("rol", "VENDEDOR");
    }
}
