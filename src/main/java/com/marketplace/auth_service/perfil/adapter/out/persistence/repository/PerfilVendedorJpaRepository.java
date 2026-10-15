package com.marketplace.auth_service.perfil.adapter.out.persistence.repository;

import com.marketplace.auth_service.perfil.adapter.out.persistence.entity.PerfilVendedorJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PerfilVendedorJpaRepository extends JpaRepository<PerfilVendedorJpaEntity, UUID> {
}
