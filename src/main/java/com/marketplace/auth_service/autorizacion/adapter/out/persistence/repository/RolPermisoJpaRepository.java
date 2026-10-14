package com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.RolPermisoJpaEntity;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Lectura de la matriz rol → permiso. Sin métodos de escritura: el catálogo se siembra por migración. */
public interface RolPermisoJpaRepository extends Repository<RolPermisoJpaEntity, RolPermisoJpaEntity.Id> {

    List<RolPermisoJpaEntity> findAll();
}
