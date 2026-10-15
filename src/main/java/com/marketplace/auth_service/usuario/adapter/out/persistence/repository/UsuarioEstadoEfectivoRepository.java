package com.marketplace.auth_service.usuario.adapter.out.persistence.repository;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioEstadoEfectivoJpaEntity;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UsuarioEstadoEfectivoRepository extends JpaRepository<UsuarioEstadoEfectivoJpaEntity, UUID> {
    Optional<UsuarioEstadoEfectivoJpaEntity> findById(UUID id);

    @Query("SELECT u FROM UsuarioEstadoEfectivoJpaEntity u WHERE (:estado IS NULL OR u.estado = :estado) ORDER BY u.id ASC")
    Page<UsuarioEstadoEfectivoJpaEntity> findByFiltro(
            @Param("estado") EstadoCuenta estado,
            Pageable pageable);

    @Query("SELECT COUNT(u) FROM UsuarioEstadoEfectivoJpaEntity u WHERE u.id IN :ids AND u.estado = 'ACTIVO'")
    long countActiveByIds(@Param("ids") Set<UUID> ids);
}