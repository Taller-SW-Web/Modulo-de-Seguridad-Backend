package com.marketplace.auth_service.perfil.adapter.out.persistence.repository;

import com.marketplace.auth_service.perfil.adapter.out.persistence.entity.PerfilClienteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PerfilClienteJpaRepository extends JpaRepository<PerfilClienteJpaEntity, UUID> {
}
