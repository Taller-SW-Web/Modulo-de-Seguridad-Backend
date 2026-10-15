package com.marketplace.auth_service.sesion.adapter.out.persistence.repository;

import com.marketplace.auth_service.sesion.adapter.out.persistence.entity.SesionFamiliaJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface SesionFamiliaJpaRepository extends JpaRepository<SesionFamiliaJpaEntity, UUID> {
    /** SesionesPort bloquea la raíz con PESSIMISTIC_WRITE, después del usuario. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from SesionFamiliaJpaEntity f where f.id = :id")
    Optional<SesionFamiliaJpaEntity> bloquearPorId(@Param("id") UUID id);
}
