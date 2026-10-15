package com.marketplace.auth_service.mfa.adapter.out.persistence.repository;

import com.marketplace.auth_service.mfa.adapter.out.persistence.entity.DesafioMfaJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface DesafioMfaJpaRepository extends JpaRepository<DesafioMfaJpaEntity, UUID> {
    Optional<DesafioMfaJpaEntity> findByHashToken(String hashToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DesafioMfaJpaEntity d where d.id = :id")
    Optional<DesafioMfaJpaEntity> bloquearPorId(@Param("id") UUID id);
}
