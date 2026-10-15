package com.marketplace.auth_service.auditoria.adapter.out.persistence.repository;

import com.marketplace.auth_service.auditoria.adapter.out.persistence.entity.AuditoriaSeguridadJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditoriaSeguridadJpaRepository extends JpaRepository<AuditoriaSeguridadJpaEntity, Long> {
    boolean existsByRegistroId(UUID registroId);
}
