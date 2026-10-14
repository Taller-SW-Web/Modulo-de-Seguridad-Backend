package com.marketplace.auth_service.credencial.adapter.out.persistence.repository;

import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.PasswordHistorialJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistorialJpaRepository extends JpaRepository<PasswordHistorialJpaEntity, UUID> {
    List<PasswordHistorialJpaEntity> findTop5ByUsuarioIdOrderByCreadoEnDescIdDesc(UUID usuarioId);
}
