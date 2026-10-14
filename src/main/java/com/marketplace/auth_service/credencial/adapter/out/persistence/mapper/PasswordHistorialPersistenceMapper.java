package com.marketplace.auth_service.credencial.adapter.out.persistence.mapper;

import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.PasswordHistorialJpaEntity;
import com.marketplace.auth_service.credencial.domain.model.AlgoritmoHash;
import com.marketplace.auth_service.credencial.domain.model.HashContrasena;
import com.marketplace.auth_service.credencial.domain.model.PasswordHistorial;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PasswordHistorialPersistenceMapper {
    private PasswordHistorialPersistenceMapper() {
    }

    public static PasswordHistorial aDominio(List<PasswordHistorialJpaEntity> entidades) {
        Objects.requireNonNull(entidades, "entidades");
        return new PasswordHistorial(entidades.stream()
                .map(entidad -> new PasswordHistorial.Entrada(
                        entidad.getId(),
                        new HashContrasena(
                                entidad.getPasswordHash(),
                                AlgoritmoHash.desdeHashCodificado(entidad.getPasswordHash())),
                        entidad.getCreadoEn().toInstant()))
                .toList());
    }

    public static List<PasswordHistorialJpaEntity> aEntidades(UUID credencialId, PasswordHistorial modelo) {
        Objects.requireNonNull(credencialId, "credencialId");
        Objects.requireNonNull(modelo, "modelo");
        return modelo.entradas().stream()
                .map(entrada -> new PasswordHistorialJpaEntity(
                        entrada.id(),
                        credencialId,
                        entrada.hash().valor(),
                        entrada.creadoEn().atOffset(ZoneOffset.UTC)))
                .toList();
    }
}
