package com.marketplace.auth_service.auditoria.adapter.out.persistence.entity;

import com.marketplace.auth_service.auditoria.domain.model.ActorTipo;
import com.marketplace.auth_service.auditoria.domain.model.Resultado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name = "auditoria_seguridad") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor
public class AuditoriaSeguridadJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "registro_id", nullable = false, unique = true) private UUID registroId;
    @Column(nullable = false, length = 50) private String accion;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "resultado") private Resultado resultado;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "actor_tipo", nullable = false, columnDefinition = "actor_tipo")
    private ActorTipo actorTipo;
    @Column(name = "actor_id", length = 64) private String actorId;
    @Column(name = "objetivo_usuario_id") private UUID objetivoUsuarioId;
    @Column(nullable = false) private OffsetDateTime fecha;
    @Column(length = 45) private String ip;
    @Column(length = 255) private String agente;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Map<String, String> detalle;
    @Override public String toString() { return "AuditoriaSeguridadJpaEntity[id=" + id + ", REDACTADO]"; }
}
