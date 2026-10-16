package com.marketplace.auth_service.auditoria.adapter.in.worker;

import com.marketplace.auth_service.auditoria.application.service.MensajeriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Sondea el outbox cada 2 s y entrega los correos pendientes (TEC-INFRA-T004). */
public class EnvioCorreosWorker {
    private static final Logger log = LoggerFactory.getLogger(EnvioCorreosWorker.class);
    private final MensajeriaService mensajeria;

    public EnvioCorreosWorker(MensajeriaService mensajeria) {
        this.mensajeria = mensajeria;
    }

    @Scheduled(fixedDelayString = "${g7.outbox.sondeo-ms:2000}", initialDelayString = "${g7.outbox.sondeo-ms:2000}")
    public void entregar() {
        try {
            int n = mensajeria.procesarCorreos();
            if (n > 0) {
                log.info("outbox: {} correo(s) entregado(s) al SMTP", n);
            }
        } catch (RuntimeException e) {
            log.warn("outbox: ciclo fallido ({})", e.getClass().getSimpleName());
        }
    }
}
