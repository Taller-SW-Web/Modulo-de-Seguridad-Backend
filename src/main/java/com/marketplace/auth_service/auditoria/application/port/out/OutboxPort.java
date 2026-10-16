package com.marketplace.auth_service.auditoria.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox durable (TEC-INFRA-T003, V106). {@link #guardar} participa en la transacción del
 * productor; reclamar y marcar abren la suya (lote con SKIP LOCKED y lease).
 */
public interface OutboxPort {

    void guardar(Mensaje mensaje);

    /** Reserva hasta {@code lote} correos pendientes vencidos para reintento; nunca dos workers el mismo. */
    List<Mensaje> reclamarCorreos(int lote, Instant ahora, Instant reservadoHasta);

    /** Enviado: marca ENVIADO y purga el contenido sensible del payload. */
    void marcarEnviado(UUID id, Instant ahora);

    /** Falla: suma un intento y reprograma, o marca FALLIDO si {@code definitivo}. */
    void marcarFallo(UUID id, String error, Instant proximoIntento, boolean definitivo);

    record Mensaje(UUID id, String topico, Map<String, String> payload, int intentos) {
        public Mensaje {
            payload = Map.copyOf(payload);
        }

        @Override
        public String toString() {
            return "Mensaje[id=" + id + ", topico=" + topico + ", REDACTADO]";
        }
    }
}
