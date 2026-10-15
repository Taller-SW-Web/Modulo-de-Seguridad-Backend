package com.marketplace.auth_service.proteccion.adapter.out.persistence.repository;

import com.marketplace.auth_service.proteccion.adapter.out.persistence.entity.RachaFalloJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RachaFalloJpaRepository extends JpaRepository<RachaFalloJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RachaFalloJpaEntity r where r.usuarioId = :usuario")
    Optional<RachaFalloJpaEntity> bloquear(@Param("usuario") UUID usuario);

    @Modifying(flushAutomatically = true)
    @Query(value = "insert into racha_fallo(usuario_id,racha_id,hash_key_id,creado_en) "
            + "values (:usuario,:racha,:clave,:creado)", nativeQuery = true)
    void insertar(@Param("usuario") UUID usuario, @Param("racha") UUID racha,
                  @Param("clave") String clave, @Param("creado") OffsetDateTime creado);
}
