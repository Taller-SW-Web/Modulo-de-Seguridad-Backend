package com.marketplace.auth_service.mfa.adapter.out.persistence.mapper;

import com.marketplace.auth_service.mfa.adapter.out.persistence.entity.DesafioMfaJpaEntity;
import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import java.time.ZoneOffset;

public final class DesafioMfaPersistenceMapper {
    private DesafioMfaPersistenceMapper() { }

    public static DesafioMfaJpaEntity aEntidad(DesafioMfa modelo) {
        if (modelo.credencialId() == null) {
            throw new IllegalArgumentException("No se persiste un desafío nuevo sin revisión de credencial");
        }
        return new DesafioMfaJpaEntity(modelo.id(), modelo.usuarioId(), modelo.credencialId(),
                modelo.credencialVersion(), modelo.hashToken(), modelo.creadoEn().atOffset(ZoneOffset.UTC),
                modelo.expiraEn().atOffset(ZoneOffset.UTC), modelo.consumido());
    }

    public static DesafioMfa aDominio(DesafioMfaJpaEntity fila) {
        return new DesafioMfa(fila.getId(), fila.getUsuarioId(), fila.getCredencialId(),
                fila.getCredencialVersion(), fila.getHashToken(), fila.getCreadoEn().toInstant(),
                fila.getExpiracion().toInstant(), fila.isConsumido());
    }
}
