package com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.RolJpaEntity;
import com.marketplace.auth_service.autorizacion.domain.model.Rol;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Lectura del catálogo de roles. Sin métodos de escritura. */
public interface CatalogoJpaRepository extends Repository<RolJpaEntity, Rol> {

    List<RolJpaEntity> findAll();
}
