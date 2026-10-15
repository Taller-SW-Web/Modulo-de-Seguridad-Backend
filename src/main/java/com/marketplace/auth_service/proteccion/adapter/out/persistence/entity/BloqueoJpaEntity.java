package com.marketplace.auth_service.proteccion.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.marketplace.auth_service.proteccion.domain.model.TipoBloqueo;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "bloqueo") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class BloqueoJpaEntity {
    @Id @Column(name = "usuario_id") private UUID usuarioId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "tipo_bloqueo") private TipoBloqueo tipo;
    @Column(name = "bloqueado_hasta") private OffsetDateTime bloqueadoHasta;
    @Column(length = 500) private String motivo;
    @Column(name = "creado_en", nullable = false) private OffsetDateTime creadoEn;
    @Column(nullable = false) private UUID revision;
    @Override public String toString() { return "BloqueoJpaEntity[usuarioId=" + usuarioId + "]"; }
}
