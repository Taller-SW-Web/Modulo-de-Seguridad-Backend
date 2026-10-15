package com.marketplace.auth_service.sesion.adapter.out.persistence.repository;

import com.marketplace.auth_service.sesion.adapter.out.persistence.entity.TokenRefrescoJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TokenRefrescoJpaRepository extends JpaRepository<TokenRefrescoJpaEntity, UUID> {
    interface TokenActual {
        UUID getJti();
        UUID getUsuarioId();
        UUID getFamiliaId();
        String getHashToken();
        boolean getRevocado();
        boolean getUsado();
        java.time.OffsetDateTime getExpiracion();
        java.time.OffsetDateTime getCreadoEn();
    }

    @Query("""
            select t.jti as jti, t.usuarioId as usuarioId, t.familiaId as familiaId,
                   t.hashToken as hashToken, t.revocado as revocado, t.usado as usado,
                   t.expiracion as expiracion, t.creadoEn as creadoEn
            from TokenRefrescoJpaEntity t where t.hashToken = :hash
            """)
    Optional<TokenActual> leerActualPorHash(@Param("hash") String hash);

    @Query("""
            select t.jti as jti, t.usuarioId as usuarioId, t.familiaId as familiaId,
                   t.hashToken as hashToken, t.revocado as revocado, t.usado as usado,
                   t.expiracion as expiracion, t.creadoEn as creadoEn
            from TokenRefrescoJpaEntity t where t.familiaId = :familia
            order by t.jti
            """)
    List<TokenActual> leerActualesDeFamilia(@Param("familia") UUID familia);

    Optional<TokenRefrescoJpaEntity> findByHashToken(String hashToken);

    List<TokenRefrescoJpaEntity> findByFamiliaIdOrderByJtiAsc(UUID familiaId);
}
