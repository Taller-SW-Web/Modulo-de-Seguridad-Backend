package com.marketplace.auth_service.mfa.adapter.out.persistence.repository;

import com.marketplace.auth_service.mfa.adapter.out.persistence.entity.OtpJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpJpaRepository extends JpaRepository<OtpJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OtpJpaEntity o where o.id = :id")
    Optional<OtpJpaEntity> bloquearPorId(@Param("id") UUID id);

    List<OtpJpaEntity> findByDesafioIdAndMotivoOrderByCreadoEnDescIdDesc(UUID desafioId, String motivo);

    // Cuenta usados/invalidados y todos los desafíos; el límite es exclusivo.
    long countByUsuarioIdAndCreadoEnGreaterThan(UUID usuarioId, OffsetDateTime limite);

    Optional<OtpJpaEntity> findFirstByUsuarioIdAndCreadoEnGreaterThanOrderByCreadoEnAscIdAsc(
            UUID usuarioId, OffsetDateTime limite);
}
