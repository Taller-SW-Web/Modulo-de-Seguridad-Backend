package com.marketplace.auth_service.usuario.adapter.out.persistence.repository;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioEstadoEfectivoJpaEntity;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Solo lectura; no expone save/delete. */
public interface UsuarioEstadoEfectivoRepository extends Repository<UsuarioEstadoEfectivoJpaEntity, UUID> {
    Optional<UsuarioEstadoEfectivoJpaEntity> findById(UUID id);
    List<UsuarioEstadoEfectivoJpaEntity> findAllById(Iterable<UUID> ids);
    @Query("SELECT COUNT(u) FROM UsuarioEstadoEfectivoJpaEntity u WHERE u.id IN :ids AND u.estado = 'ACTIVO'")
    long countActiveByIds(@Param("ids") Set<UUID> ids);
}
