package com.marketplace.auth_service.sesion.adapter.out.persistence.repository;

import com.marketplace.auth_service.sesion.adapter.out.persistence.entity.TokenRefrescoJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TokenRefrescoJpaRepository extends JpaRepository<TokenRefrescoJpaEntity, UUID> {
    Optional<TokenRefrescoJpaEntity> findByHashToken(String hashToken);

    List<TokenRefrescoJpaEntity> findByFamiliaIdOrderByJtiAsc(UUID familiaId);
}
