package com.marketplace.auth_service.mfa.adapter.out.persistence.entity;

import com.marketplace.auth_service.mfa.domain.model.CanalOtp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Columnas de V2 real + propuesta V103: canal varchar, expira_en y motivo se conservan. */
@Entity
@Table(name = "otp")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OtpJpaEntity {
    @Id private UUID id;
    @Column(name = "usuario_id", nullable = false) private UUID usuarioId;
    @Column(name = "desafio_id") private UUID desafioId;
    @Column(name = "hash_codigo", nullable = false, length = 255) private String hashCodigo;
    @Column(name = "hash_key_id", length = 50) private String hashKeyId;
    @Column(nullable = false, length = 255) private String destino;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50) private CanalOtp canal;
    @Column(nullable = false, length = 50) private String motivo;
    @Column(name = "creado_en", nullable = false) private OffsetDateTime creadoEn;
    @Column(name = "expira_en", nullable = false) private OffsetDateTime expiraEn;
    @Column(nullable = false) private boolean usado;
    @Column(nullable = false) private int intentos;
    @Column(name = "invalidado_en") private OffsetDateTime invalidadoEn;

    @Override public String toString() { return "OtpJpaEntity[id=" + id + ", canal=" + canal + "]"; }
}
