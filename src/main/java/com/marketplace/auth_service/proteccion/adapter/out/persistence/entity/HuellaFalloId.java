package com.marketplace.auth_service.proteccion.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.io.Serializable;
import java.util.UUID;

@Embeddable @Getter @EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class HuellaFalloId implements Serializable {
    @Column(name = "usuario_id", nullable = false) private UUID usuarioId;
    @Column(name = "racha_id", nullable = false) private UUID rachaId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "huella_hmac", nullable = false, length = 64, columnDefinition = "char(64)")
    private String huellaHmac;
    @Override public String toString() { return "HuellaFalloId[usuarioId=" + usuarioId + "]"; }
}
