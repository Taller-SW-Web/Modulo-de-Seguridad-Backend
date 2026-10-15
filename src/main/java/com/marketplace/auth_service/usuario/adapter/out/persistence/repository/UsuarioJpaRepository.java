package com.marketplace.auth_service.usuario.adapter.out.persistence.repository;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UsuarioJpaRepository extends JpaRepository<UsuarioJpaEntity, UUID> {
    Optional<UsuarioJpaEntity> findByCorreo(String correo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UsuarioJpaEntity u WHERE u.id = :id")
    Optional<UsuarioJpaEntity> lockById(@Param("id") UUID id);

    @Query("SELECT COUNT(u) FROM UsuarioJpaEntity u WHERE u.id IN :ids AND u.estado = 'ACTIVO'")
    long countActiveByIds(@Param("ids") Set<UUID> ids);

    @Query("SELECT u FROM UsuarioJpaEntity u WHERE (:estado IS NULL OR u.estado = :estado) AND (:correoFragmento IS NULL OR LOWER(u.correo) LIKE LOWER(CONCAT('%', :correoFragmento, '%'))) ORDER BY u.creadoEn DESC")
    Page<UsuarioJpaEntity> findByFiltro(
            @Param("estado") EstadoCuenta estado,
            @Param("correoFragmento") String correoFragmento,
            Pageable pageable);
}