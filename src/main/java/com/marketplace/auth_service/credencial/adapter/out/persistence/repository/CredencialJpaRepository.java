package com.marketplace.auth_service.credencial.adapter.out.persistence.repository;

import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.CredencialJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredencialJpaRepository extends JpaRepository<CredencialJpaEntity, UUID> {
    Optional<CredencialJpaEntity> findByUsuarioId(UUID usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CredencialJpaEntity c where c.usuarioId = :usuarioId")
    Optional<CredencialJpaEntity> bloquearPorUsuarioId(@Param("usuarioId") UUID usuarioId);
}
