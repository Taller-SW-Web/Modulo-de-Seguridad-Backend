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
    /** Proyección de valores actuales; no materializa una entidad cacheada en L1. */
    interface LecturaActual {
        UUID getUsuarioId();
        String getPasswordHash();
        java.time.OffsetDateTime getActualizadoEn();
        Long getVersion();
    }

    @Query("""
            select c.usuarioId as usuarioId, c.passwordHash as passwordHash,
                   c.actualizadoEn as actualizadoEn, c.version as version
            from CredencialJpaEntity c where c.usuarioId = :usuarioId
            """)
    Optional<LecturaActual> leerActual(@Param("usuarioId") UUID usuarioId);

    @Query("""
            select (count(c) > 0) from CredencialJpaEntity c
            where c.usuarioId = :usuarioId and c.version = :version
            """)
    boolean existeRevision(@Param("usuarioId") UUID usuarioId, @Param("version") long version);

    Optional<CredencialJpaEntity> findByUsuarioId(UUID usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CredencialJpaEntity c where c.usuarioId = :usuarioId")
    Optional<CredencialJpaEntity> bloquearPorUsuarioId(@Param("usuarioId") UUID usuarioId);
}
