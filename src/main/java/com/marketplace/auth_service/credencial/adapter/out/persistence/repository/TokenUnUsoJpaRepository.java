package com.marketplace.auth_service.credencial.adapter.out.persistence.repository;

import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.TokenUnUsoJpaEntity;
import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.PropositoTokenPersistido;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenUnUsoJpaRepository extends JpaRepository<TokenUnUsoJpaEntity, UUID> {
    Optional<TokenUnUsoJpaEntity> findByHashTokenAndProposito(String hashToken, PropositoTokenPersistido proposito);

    Optional<TokenUnUsoJpaEntity> findByHashTokenAndPropositoAndContextoIdIsNotNull(
            String hashToken, PropositoTokenPersistido proposito);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenUnUsoJpaEntity t where t.id = :id")
    Optional<TokenUnUsoJpaEntity> bloquearPorId(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TokenUnUsoJpaEntity t set t.usado = true where t.usuarioId = :usuarioId " +
            "and t.proposito = :proposito and t.usado = false and t.expiraEn > :ahora")
    int invalidarVigentes(@Param("usuarioId") UUID usuarioId,
                          @Param("proposito") PropositoTokenPersistido proposito,
                          @Param("ahora") OffsetDateTime ahora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TokenUnUsoJpaEntity t set t.usado = true where t.id = :id and t.usado = false")
    int consumir(@Param("id") UUID id);
}
