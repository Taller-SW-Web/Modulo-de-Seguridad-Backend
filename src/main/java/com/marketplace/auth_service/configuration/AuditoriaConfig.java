package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.adapter.in.transaction.AuditoriaTransaccional;
import com.marketplace.auth_service.auditoria.adapter.in.worker.EnvioCorreosWorker;
import com.marketplace.auth_service.auditoria.adapter.out.correo.EnvioCorreoSmtp;
import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesAesGcm;
import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesDenegado;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.OutboxJpaAdapter;
import com.marketplace.auth_service.auditoria.adapter.out.persistence.repository.OutboxJpaRepository;
import com.marketplace.auth_service.auditoria.adapter.out.transaccion.TareasTrasCommitSpring;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.out.AuditoriaPersistenciaPort;
import com.marketplace.auth_service.auditoria.application.port.out.CifradoMensajesPort;
import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import com.marketplace.auth_service.auditoria.application.service.AuditoriaService;
import com.marketplace.auth_service.auditoria.application.service.MensajeriaService;
import java.time.Clock;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Auditoría (EP-06-T002–T004) y mensajería por outbox (TEC-INFRA-T003/T004). El cifrado de
 * correos usa {@code AUTH_OUTBOX_AES_KEY_ID}/{@code AUTH_OUTBOX_AES_KEY} (Base64, 32 bytes);
 * sin ellas ningún correo se guarda y su productor revierte (503).
 */
@Configuration(proxyBeanMethods = false)
public class AuditoriaConfig {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaConfig.class);

    @Bean
    AuditoriaPort auditoriaPort(AuditoriaPersistenciaPort persistencia, PlatformTransactionManager transacciones) {
        return new AuditoriaTransaccional(new AuditoriaService(persistencia, new TareasTrasCommitSpring(transacciones)));
    }

    @Bean
    CifradoMensajesPort cifradoMensajesPort(Environment entorno) {
        String id = entorno.getProperty("AUTH_OUTBOX_AES_KEY_ID");
        String material = entorno.getProperty("AUTH_OUTBOX_AES_KEY");
        if (id == null || id.isBlank() || material == null || material.isBlank()) {
            log.warn("Mensajería: correos denegados; faltan AUTH_OUTBOX_AES_KEY_ID/AUTH_OUTBOX_AES_KEY");
            return new CifradoMensajesDenegado();
        }
        try {
            return new CifradoMensajesAesGcm(id, Base64.getDecoder().decode(material));
        } catch (IllegalArgumentException e) {
            log.error("Mensajería: correos denegados; clave de outbox inválida (Base64 de 32 bytes, id sin ':')");
            return new CifradoMensajesDenegado();
        }
    }

    /** Bean propio para que Spring aplique sus transacciones (MANDATORY al guardar, REQUIRES_NEW al reclamar). */
    @Bean
    OutboxPort outboxPort(OutboxJpaRepository filas) {
        return new OutboxJpaAdapter(filas);
    }

    @Bean
    MensajeriaService mensajeriaService(OutboxPort outbox, CifradoMensajesPort cifrado,
                                        ObjectProvider<JavaMailSender> correo,
                                        @Value("${g7.correo.remitente:${spring.mail.username:}}") String remitente,
                                        ObjectProvider<Clock> reloj) {
        return new MensajeriaService(outbox, cifrado,
                new EnvioCorreoSmtp(correo.getIfAvailable(), remitente), reloj.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    EnvioCorreosWorker envioCorreosWorker(MensajeriaService mensajeria) {
        return new EnvioCorreosWorker(mensajeria);
    }
}
