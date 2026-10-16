package com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.RolJpaEntity;
import com.marketplace.auth_service.shared.domain.Rol;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Lectura y bloqueo del catálogo de roles. Sin métodos de escritura. */
public interface CatalogoJpaRepository extends Repository<RolJpaEntity, Rol> {

    List<RolJpaEntity> findAll();

    /**
     * Bloquea la fila fija de {@code ADMIN_SISTEMA} hasta el fin de la transacción.
     * Devuelve el código bloqueado, o nada si la fila no existe.
     */
    @Query(value = "SELECT CAST(codigo AS text) FROM rol WHERE codigo = 'ADMIN_SISTEMA' FOR UPDATE",
            nativeQuery = true)
    List<String> bloquearFilaAdministrador();
}
