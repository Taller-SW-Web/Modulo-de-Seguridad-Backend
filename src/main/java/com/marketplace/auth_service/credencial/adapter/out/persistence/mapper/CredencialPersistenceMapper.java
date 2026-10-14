package com.marketplace.auth_service.credencial.adapter.out.persistence.mapper;

import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.CredencialJpaEntity;
import com.marketplace.auth_service.credencial.domain.model.AlgoritmoHash;
import com.marketplace.auth_service.credencial.domain.model.Credencial;
import com.marketplace.auth_service.credencial.domain.model.HashContrasena;
import java.time.ZoneOffset;
import java.util.Objects;

public final class CredencialPersistenceMapper {
    private CredencialPersistenceMapper() {
    }

    public static CredencialJpaEntity aNuevaEntidad(Credencial modelo) {
        Objects.requireNonNull(modelo, "modelo");
        if (!modelo.id().equals(modelo.usuarioId())) {
            throw new IllegalArgumentException("La identidad persistida de credencial es usuario_id");
        }
        if (modelo.version() != 0) {
            throw new IllegalArgumentException("Una credencial nueva debe iniciar en versión cero");
        }
        return new CredencialJpaEntity(
                modelo.usuarioId(),
                modelo.hashActual().valor(),
                modelo.actualizadoEn().atOffset(ZoneOffset.UTC));
    }

    public static Credencial aDominio(CredencialJpaEntity entidad) {
        Objects.requireNonNull(entidad, "entidad");
        if (entidad.getVersion() == null) {
            throw new IllegalStateException("La credencial persistida no tiene revisión");
        }
        return new Credencial(
                entidad.getId(),
                entidad.getUsuarioId(),
                new HashContrasena(
                        entidad.getPasswordHash(),
                        AlgoritmoHash.desdeHashCodificado(entidad.getPasswordHash())),
                entidad.getActualizadoEn().toInstant(),
                entidad.getVersion());
    }
}
