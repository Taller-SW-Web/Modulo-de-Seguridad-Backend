package com.marketplace.auth_service.usuario.adapter.out.persistence.repository;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioJpaRepository extends JpaRepository<UsuarioJpaEntity, UUID>, JpaSpecificationExecutor<UsuarioJpaEntity> {
    Optional<UsuarioJpaEntity> findByCorreo(String correo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UsuarioJpaEntity u WHERE u.id = :id")
    Optional<UsuarioJpaEntity> lockById(@Param("id") UUID id);
}
