package com.marketplace.auth_service.sesion.adapter.out.persistence.repository;

import com.marketplace.auth_service.sesion.adapter.out.persistence.entity.AccesoSesionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AccesoSesionJpaRepository extends JpaRepository<AccesoSesionJpaEntity, UUID> {
}
