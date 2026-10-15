package com.marketplace.auth_service.proteccion.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "racha_fallo") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class RachaFalloJpaEntity {
    @Id @Column(name = "usuario_id") private UUID usuarioId;
    @Column(name = "racha_id", nullable = false) private UUID rachaId;
    @Column(name = "hash_key_id", nullable = false, length = 50) private String hashKeyId;
    @Column(name = "creado_en", nullable = false) private OffsetDateTime creadoEn;
    @Override public String toString() { return "RachaFalloJpaEntity[usuarioId=" + usuarioId + "]"; }
}
