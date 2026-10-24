package com.marketplace.auth_service.direccion.adapter.out.persistence.repository;

import com.marketplace.auth_service.direccion.adapter.out.persistence.entity.DireccionJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DireccionJpaRepository extends JpaRepository<DireccionJpaEntity, UUID> {
    List<DireccionJpaEntity> findAllByUsuarioIdOrderByEsPredeterminadaDescIdAsc(UUID usuarioId);
}
