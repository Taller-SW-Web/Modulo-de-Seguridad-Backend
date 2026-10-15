package com.marketplace.auth_service.sesion.adapter.out.persistence.repository;

import com.marketplace.auth_service.sesion.adapter.out.persistence.entity.AccesoSesionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface AccesoSesionJpaRepository extends JpaRepository<AccesoSesionJpaEntity, UUID> {
    /** Cada emisión inserta: un jti repetido no puede reasignar un acceso existente. */
    @Modifying
    @Query(value = """
            insert into acceso_sesion (jti, usuario_id, familia_id, expira_en, creado_en)
            values (:jti, :usuarioId, :familiaId, :expiraEn, :creadoEn)
            """, nativeQuery = true)
    int insertar(@Param("jti") UUID jti, @Param("usuarioId") UUID usuarioId,
                 @Param("familiaId") UUID familiaId, @Param("expiraEn") OffsetDateTime expiraEn,
                 @Param("creadoEn") OffsetDateTime creadoEn);
}
