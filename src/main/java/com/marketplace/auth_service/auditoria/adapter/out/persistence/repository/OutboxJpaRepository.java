package com.marketplace.auth_service.auditoria.adapter.out.persistence.repository;

import com.marketplace.auth_service.auditoria.adapter.out.persistence.entity.OutboxJpaEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    /** Reserva un lote: SKIP LOCKED evita que dos workers tomen la misma fila. */
    @Modifying
    @Query(value = """
            UPDATE outbox SET reservado_hasta = :hasta, reserva_id = :reserva
            WHERE id IN (SELECT id FROM outbox
                         WHERE estado = 'PENDIENTE' AND topico LIKE 'correo.%'
                           AND (proximo_intento_en IS NULL OR proximo_intento_en <= :ahora)
                           AND (reservado_hasta IS NULL OR reservado_hasta < :ahora)
                         ORDER BY creado_en LIMIT :lote FOR UPDATE SKIP LOCKED)""", nativeQuery = true)
    int reservar(@Param("lote") int lote, @Param("ahora") OffsetDateTime ahora,
                 @Param("hasta") OffsetDateTime hasta, @Param("reserva") UUID reserva);

    List<OutboxJpaEntity> findByReservaId(UUID reserva);

    /** Enviado: purga el contenido sensible (destino y texto cifrados). */
    @Modifying
    @Query(value = """
            UPDATE outbox SET estado = 'ENVIADO', procesado_en = :ahora, payload = CAST('{}' AS jsonb),
                   reservado_hasta = NULL, reserva_id = NULL, ultimo_error = NULL
            WHERE id = :id""", nativeQuery = true)
    int marcarEnviado(@Param("id") UUID id, @Param("ahora") OffsetDateTime ahora);

    @Modifying
    @Query(value = """
            UPDATE outbox SET intentos = intentos + 1, ultimo_error = :error, proximo_intento_en = :proximo,
                   estado = CASE WHEN :definitivo THEN 'FALLIDO' ELSE estado END,
                   payload = CASE WHEN :definitivo THEN CAST('{}' AS jsonb) ELSE payload END,
                   reservado_hasta = NULL, reserva_id = NULL
            WHERE id = :id""", nativeQuery = true)
    int marcarFallo(@Param("id") UUID id, @Param("error") String error, @Param("proximo") OffsetDateTime proximo,
                    @Param("definitivo") boolean definitivo);
}
