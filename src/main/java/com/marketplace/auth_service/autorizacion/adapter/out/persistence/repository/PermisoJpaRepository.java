package com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.PermisoJpaEntity;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Lectura del catálogo de permisos. Sin métodos de escritura: se siembra por migración. */
public interface PermisoJpaRepository extends Repository<PermisoJpaEntity, String> {

    List<PermisoJpaEntity> findAll();
}
