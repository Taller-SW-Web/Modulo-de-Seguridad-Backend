package com.marketplace.auth_service.auditoria.adapter.out.persistence;

import com.marketplace.auth_service.auditoria.adapter.out.persistence.entity.OutboxJpaEntity;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.repository.OutboxJpaRepository;
import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Outbox sobre JPA: guardar en el commit del productor; reclamar y marcar en transacciones propias. */
public class OutboxJpaAdapter implements OutboxPort {
    private final OutboxJpaRepository filas;

    public OutboxJpaAdapter(OutboxJpaRepository filas) {
        this.filas = filas;
    }

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void guardar(Mensaje m) {
        filas.saveAndFlush(new OutboxJpaEntity(m.id(), m.topico(), new HashMap<>(m.payload()), utc(Instant.now())));
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Mensaje> reclamarCorreos(int lote, Instant ahora, Instant reservadoHasta) {
        UUID reserva = UUID.randomUUID();
        if (filas.reservar(lote, utc(ahora), utc(reservadoHasta), reserva) == 0) {
            return List.of();
        }
        return filas.findByReservaId(reserva).stream()
                .map(f -> new Mensaje(f.getId(), f.getTopico(), f.getPayload(), f.getIntentos())).toList();
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Mensaje> reclamarEventos(int lote, Instant ahora, Instant reservadoHasta) {
        UUID reserva = UUID.randomUUID();
        if (filas.reservarEventos(lote, utc(ahora), utc(reservadoHasta), reserva) == 0) {
            return List.of();
        }
        return filas.findByReservaId(reserva).stream()
                .map(f -> new Mensaje(f.getId(), f.getTopico(), f.getPayload(), f.getIntentos())).toList();
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarEnviado(UUID id, Instant ahora) {
        filas.marcarEnviado(id, utc(ahora));
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarFallo(UUID id, String error, Instant proximoIntento, boolean definitivo) {
        filas.marcarFallo(id, error, utc(proximoIntento), definitivo);
    }
}
