package com.marketplace.auth_service.usuario.adapter.out.persistence.repository;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioEstadoEfectivoJpaEntity;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.domain.model.CodigoRol;
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

    @Query("SELECT u FROM UsuarioEstadoEfectivoJpaEntity u WHERE (:estado IS NULL OR u.estadoEfectivo = :estado) AND (:correoFragmento IS NULL OR LOWER(u.correo) LIKE LOWER(CONCAT('%', :correoFragmento, '%'))) AND (:rolIds IS NULL OR u.id IN (SELECT ur.usuarioId FROM UsuarioRolJpaEntity ur WHERE ur.rol IN :rolIds)) ORDER BY u.createdAt DESC")
    Page<UsuarioEstadoEfectivoJpaEntity> findByFiltro(
            @Param("estado") EstadoCuenta estado,
            @Param("correoFragmento") String correoFragmento,
            @Param("rolIds") Set<UUID> rolIds,
            Pageable pageable);

    @Query("SELECT COUNT(u) FROM UsuarioEstadoEfectivoJpaEntity u WHERE (:estado IS NULL OR u.estadoEfectivo = :estado) AND (:correoFragmento IS NULL OR LOWER(u.correo) LIKE LOWER(CONCAT('%', :correoFragmento, '%'))) AND (:rolIds IS NULL OR u.id IN (SELECT ur.usuarioId FROM UsuarioRolJpaEntity ur WHERE ur.rol IN :rolIds))")
    long countByFiltro(
            @Param("estado") EstadoCuenta estado,
            @Param("correoFragmento") String correoFragmento,
            @Param("rolIds") Set<UUID> rolIds);
}