package com.marketplace.auth_service.sesion.adapter.out.persistence.mapper;

import com.marketplace.auth_service.sesion.adapter.out.persistence.entity.AccesoSesionJpaEntity;
import com.marketplace.auth_service.sesion.adapter.out.persistence.entity.SesionFamiliaJpaEntity;
import com.marketplace.auth_service.sesion.adapter.out.persistence.entity.TokenRefrescoJpaEntity;
import com.marketplace.auth_service.sesion.domain.model.AccesoSesion;
import com.marketplace.auth_service.sesion.domain.model.SesionFamilia;
import com.marketplace.auth_service.sesion.domain.model.TokenRefresco;
import java.time.ZoneOffset;

public final class SesionPersistenceMapper {
    private SesionPersistenceMapper() { }

    public static SesionFamiliaJpaEntity aEntidad(SesionFamilia modelo) {
        return new SesionFamiliaJpaEntity(modelo.id(), modelo.usuarioId(), modelo.revocada(),
                modelo.creadoEn().atOffset(ZoneOffset.UTC),
                modelo.revocadaEn() == null ? null : modelo.revocadaEn().atOffset(ZoneOffset.UTC));
    }

    public static SesionFamilia aDominio(SesionFamiliaJpaEntity fila) {
        return new SesionFamilia(fila.getId(), fila.getUsuarioId(), fila.isRevocada(),
                fila.getCreadoEn().toInstant(),
                fila.getRevocadaEn() == null ? null : fila.getRevocadaEn().toInstant());
    }

    public static TokenRefrescoJpaEntity aEntidad(TokenRefresco modelo) {
        return new TokenRefrescoJpaEntity(modelo.jti(), modelo.usuarioId(), modelo.familiaId(),
                modelo.hashToken(), modelo.revocado(), modelo.usado(),
                modelo.expiracion().atOffset(ZoneOffset.UTC), modelo.creadoEn().atOffset(ZoneOffset.UTC));
    }

    public static TokenRefresco aDominio(TokenRefrescoJpaEntity fila) {
        return new TokenRefresco(fila.getJti(), fila.getUsuarioId(), fila.getFamiliaId(),
                fila.getHashToken(), fila.isRevocado(), fila.isUsado(),
                fila.getExpiracion().toInstant(), fila.getCreadoEn().toInstant());
    }

    public static AccesoSesionJpaEntity aEntidad(AccesoSesion modelo) {
        return new AccesoSesionJpaEntity(modelo.jti(), modelo.usuarioId(), modelo.familiaId(),
                modelo.expiraEn().atOffset(ZoneOffset.UTC), modelo.creadoEn().atOffset(ZoneOffset.UTC));
    }

    public static AccesoSesion aDominio(AccesoSesionJpaEntity fila) {
        return new AccesoSesion(fila.getJti(), fila.getUsuarioId(), fila.getFamiliaId(),
                fila.getExpiraEn().toInstant(), fila.getCreadoEn().toInstant());
    }
}
