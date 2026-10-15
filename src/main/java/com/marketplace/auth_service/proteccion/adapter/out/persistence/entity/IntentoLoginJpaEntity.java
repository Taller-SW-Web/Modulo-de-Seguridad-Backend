package com.marketplace.auth_service.proteccion.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.marketplace.auth_service.proteccion.domain.model.ResultadoLogin;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "intento_login") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class IntentoLoginJpaEntity {
    @Id private UUID id;
    @Column(name = "usuario_id") private UUID usuarioId;
    @Column(name = "correo_intentado", length = 254) private String correoIntentado;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "resultado") private ResultadoLogin resultado;
    @Column(length = 45) private String ip;
    @Column(name = "user_agent", length = 255) private String agente;
    @Column(nullable = false) private OffsetDateTime fecha;
    @Override public String toString() { return "IntentoLoginJpaEntity[id=" + id + "]"; }
}
