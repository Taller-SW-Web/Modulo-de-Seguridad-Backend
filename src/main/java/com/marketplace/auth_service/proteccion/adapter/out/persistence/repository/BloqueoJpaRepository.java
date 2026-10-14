package com.marketplace.auth_service.proteccion.adapter.out.persistence.repository;

import com.marketplace.auth_service.proteccion.adapter.out.persistence.entity.BloqueoJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BloqueoJpaRepository extends JpaRepository<BloqueoJpaEntity, UUID> {
    @Query("select b from BloqueoJpaEntity b where b.usuarioId = :usuario")
    Optional<BloqueoJpaEntity> leer(@Param("usuario") UUID usuario);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BloqueoJpaEntity b where b.usuarioId = :usuario")
    Optional<BloqueoJpaEntity> bloquear(@Param("usuario") UUID usuario);

    @Modifying(flushAutomatically = true)
    @Query("delete from BloqueoJpaEntity b where b.usuarioId = :usuario and b.revision = :revision")
    int borrarRevision(@Param("usuario") UUID usuario, @Param("revision") UUID revision);
}
