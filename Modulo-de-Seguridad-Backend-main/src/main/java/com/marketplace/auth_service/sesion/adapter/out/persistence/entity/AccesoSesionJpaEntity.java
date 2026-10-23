package com.marketplace.auth_service.sesion.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "acceso_sesion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AccesoSesionJpaEntity {
    @Id private UUID jti;
    @Column(name = "usuario_id", nullable = false) private UUID usuarioId;
    @Column(name = "familia_id", nullable = false) private UUID familiaId;
    @Column(name = "expira_en", nullable = false) private OffsetDateTime expiraEn;
    @Column(name = "creado_en", nullable = false) private OffsetDateTime creadoEn;

    @Override public String toString() { return "AccesoSesionJpaEntity[jti=" + jti + "]"; }
}
