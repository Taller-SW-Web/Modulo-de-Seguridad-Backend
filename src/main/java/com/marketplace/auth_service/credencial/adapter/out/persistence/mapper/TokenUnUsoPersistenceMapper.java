package com.marketplace.auth_service.credencial.adapter.out.persistence.mapper;

import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.PropositoTokenPersistido;
import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.TokenUnUsoJpaEntity;
import com.marketplace.auth_service.credencial.domain.model.PropositoTokenUnUso;
import com.marketplace.auth_service.credencial.domain.model.TokenUnUso;
import java.time.ZoneOffset;
import java.util.Objects;

public final class TokenUnUsoPersistenceMapper {
    private TokenUnUsoPersistenceMapper() { }
    public static TokenUnUsoJpaEntity aEntidad(TokenUnUso t) {
        Objects.requireNonNull(t);
        return new TokenUnUsoJpaEntity(t.id(), aPersistido(t.proposito()), t.usuarioId(), t.hashToken(),
                t.destino(), t.expiraEn().atOffset(ZoneOffset.UTC), t.usado(),
                t.creadoEn().atOffset(ZoneOffset.UTC), t.contextoId());
    }
    public static TokenUnUso aDominio(TokenUnUsoJpaEntity e) {
        Objects.requireNonNull(e);
        return new TokenUnUso(e.getId(), e.getUsuarioId(), desdePersistido(e.getProposito()), e.getHashToken(),
                e.getContextoId(), e.getDestino(), e.getExpiraEn().toInstant(), e.getCreadoEn().toInstant(), e.isUsado());
    }
    public static PropositoTokenPersistido aPersistido(PropositoTokenUnUso p) {
        return p == PropositoTokenUnUso.DESBLOQUEO_AUTOMATICO ? PropositoTokenPersistido.DESBLOQUEO
                : PropositoTokenPersistido.valueOf(p.name());
    }
    public static PropositoTokenUnUso desdePersistido(PropositoTokenPersistido p) {
        return p == PropositoTokenPersistido.DESBLOQUEO ? PropositoTokenUnUso.DESBLOQUEO_AUTOMATICO
                : PropositoTokenUnUso.valueOf(p.name());
    }
}
