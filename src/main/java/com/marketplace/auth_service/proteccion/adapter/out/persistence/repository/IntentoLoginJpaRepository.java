package com.marketplace.auth_service.proteccion.adapter.out.persistence.repository;

import com.marketplace.auth_service.proteccion.adapter.out.persistence.entity.IntentoLoginJpaEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IntentoLoginJpaRepository extends JpaRepository<IntentoLoginJpaEntity, UUID> {
    // Insert explícito: repetir el id falla por PK; jamás sobreescribe un hecho.
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into intento_login(id,usuario_id,correo_intentado,resultado,ip,user_agent,fecha) "
            + "values (:id,:usuario,:correo,cast(:resultado as resultado),:ip,:agente,:fecha)", nativeQuery = true)
    void insertar(@Param("id") UUID id, @Param("usuario") UUID usuario, @Param("correo") String correo,
                  @Param("resultado") String resultado, @Param("ip") String ip,
                  @Param("agente") String agente, @Param("fecha") OffsetDateTime fecha);
}
