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
@Table(name = "token_refresco")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TokenRefrescoJpaEntity {
    @Id private UUID jti;
    @Column(name = "usuario_id", nullable = false) private UUID usuarioId;
    @Column(name = "familia_id", nullable = false) private UUID familiaId;
    @Column(name = "hash_token", nullable = false, length = 255) private String hashToken;
    @Column(nullable = false) private boolean revocado;
    @Column(nullable = false) private boolean usado;
    @Column(name = "expiracion", nullable = false) private OffsetDateTime expiracion;
    @Column(name = "creado_en", nullable = false) private OffsetDateTime creadoEn;

    // Sin toString: el hash nunca sale a logs, ni siquiera parcial.
}
