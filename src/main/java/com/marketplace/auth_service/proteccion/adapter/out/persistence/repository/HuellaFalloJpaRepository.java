package com.marketplace.auth_service.proteccion.adapter.out.persistence.repository;

import com.marketplace.auth_service.proteccion.adapter.out.persistence.entity.HuellaFalloJpaEntity;
import com.marketplace.auth_service.proteccion.adapter.out.persistence.entity.HuellaFalloId;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface HuellaFalloJpaRepository extends JpaRepository<HuellaFalloJpaEntity, HuellaFalloId> {
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into huella_fallo(usuario_id,racha_id,huella_hmac,creado_en) "
            + "values (:usuario,:racha,:huella,:creado) on conflict (usuario_id,racha_id,huella_hmac) do nothing",
            nativeQuery = true)
    int insertarSiNueva(@Param("usuario") UUID usuario, @Param("racha") UUID racha,
                        @Param("huella") String huella, @Param("creado") OffsetDateTime creado);

    @Query("select count(h) from HuellaFalloJpaEntity h "
            + "where h.id.usuarioId = :usuario and h.id.rachaId = :racha")
    long contar(@Param("usuario") UUID usuario, @Param("racha") UUID racha);

    @Modifying(flushAutomatically = true)
    @Query("delete from HuellaFalloJpaEntity h where h.id.usuarioId = :usuario")
    void borrarDelUsuario(@Param("usuario") UUID usuario);
}
