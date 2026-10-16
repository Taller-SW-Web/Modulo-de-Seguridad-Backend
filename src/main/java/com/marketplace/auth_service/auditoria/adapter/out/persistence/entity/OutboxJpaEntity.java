package com.marketplace.auth_service.auditoria.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fila de {@code outbox} (V4 + V106). Solo se inserta desde aquí; reclamo y marcas van por SQL nativo. */
@Entity @Table(name = "outbox") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxJpaEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String topico;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private Map<String, String> payload;
    @Column(nullable = false, length = 20) private String estado;
    @Column(name = "creado_en", nullable = false) private OffsetDateTime creadoEn;
    @Column(nullable = false) private int intentos;
    @Column(name = "procesado_en") private OffsetDateTime procesadoEn;
    @Column(name = "proximo_intento_en") private OffsetDateTime proximoIntentoEn;
    @Column(name = "reservado_hasta") private OffsetDateTime reservadoHasta;
    @Column(name = "reserva_id") private UUID reservaId;
    @Column(name = "ultimo_error", length = 500) private String ultimoError;

    public OutboxJpaEntity(UUID id, String topico, Map<String, String> payload, OffsetDateTime creadoEn) {
        this.id = id;
        this.topico = topico;
        this.payload = payload;
        this.estado = "PENDIENTE";
        this.creadoEn = creadoEn;
        this.intentos = 0;
    }

    @Override public String toString() { return "OutboxJpaEntity[id=" + id + ", REDACTADO]"; }
}
