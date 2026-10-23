package com.marketplace.auth_service.auditoria.adapter.in.worker;

import com.marketplace.auth_service.auditoria.application.port.out.PublicadorEventosPort;
import com.marketplace.auth_service.auditoria.application.service.MensajeriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Sondea el outbox y publica los eventos en RabbitMQ (TEC-INFRA-T004); solo existe si hay broker. */
public class PublicacionEventosWorker {
    private static final Logger log = LoggerFactory.getLogger(PublicacionEventosWorker.class);
    private final MensajeriaService mensajeria;
    private final PublicadorEventosPort publicador;

    public PublicacionEventosWorker(MensajeriaService mensajeria, PublicadorEventosPort publicador) {
        this.mensajeria = mensajeria;
        this.publicador = publicador;
    }

    @Scheduled(fixedDelayString = "${g7.outbox.sondeo-ms:2000}", initialDelayString = "${g7.outbox.sondeo-ms:2000}")
    public void publicar() {
        try {
            int n = mensajeria.procesarEventos(publicador);
            if (n > 0) {
                log.info("outbox: {} evento(s) publicado(s) en RabbitMQ", n);
            }
        } catch (RuntimeException e) {
            log.warn("outbox: ciclo de eventos fallido ({})", e.getClass().getSimpleName());
        }
    }
}
