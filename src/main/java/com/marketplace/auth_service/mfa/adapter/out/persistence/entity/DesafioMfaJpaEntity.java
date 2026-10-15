package com.marketplace.auth_service.mfa.adapter.out.persistence.entity;

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
@Table(name = "desafio_mfa")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DesafioMfaJpaEntity {
    @Id private UUID id;
    @Column(name = "usuario_id", nullable = false) private UUID usuarioId;
    @Column(name = "credencial_id") private UUID credencialId;
    @Column(name = "credencial_version") private Long credencialVersion;
    @Column(name = "hash_token", nullable = false, length = 255) private String hashToken;
    @Column(name = "creado_en", nullable = false) private OffsetDateTime creadoEn;
    @Column(name = "expiracion", nullable = false) private OffsetDateTime expiracion;
    @Column(nullable = false) private boolean consumido;

    @Override public String toString() { return "DesafioMfaJpaEntity[id=" + id + "]"; }
}
