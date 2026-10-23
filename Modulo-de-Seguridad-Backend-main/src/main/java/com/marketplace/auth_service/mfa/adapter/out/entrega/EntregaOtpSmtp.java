package com.marketplace.auth_service.mfa.adapter.out.entrega;

import com.marketplace.auth_service.mfa.application.port.out.EntregaOtpPort;
import com.marketplace.auth_service.mfa.domain.model.CanalOtp;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Correo real por SMTP (RF-09.4), enviado después del commit y fuera de locks. Si el envío
 * falla, el código ya quedó guardado: el titular pide otro (consume cuota, RF-09.6). El
 * código nunca se registra. SMS: adaptador simulado del entorno (RF-09.13), sin contenido en logs.
 *
 * <p>Desviación documentada de H3: no pasa por el outbox cifrado (TEC-INFRA-T003 pendiente);
 * se envía directamente tras el commit.
 */
public final class EntregaOtpSmtp implements EntregaOtpPort {

    private static final Logger log = LoggerFactory.getLogger(EntregaOtpSmtp.class);

    private final JavaMailSender correo;
    private final String remitente;

    public EntregaOtpSmtp(JavaMailSender correo, String remitente) {
        this.correo = Objects.requireNonNull(correo);
        this.remitente = Objects.requireNonNull(remitente);
    }

    @Override
    public void programar(CanalOtp canal, String destino, String codigo) {
        if (canal == CanalOtp.SMS) {
            log.info("otp: SMS simulado programado (entorno sin proveedor SMS)");
            return;
        }
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destino);
        mensaje.setSubject("Tu código de verificación");
        mensaje.setText("Tu código para iniciar sesión es " + codigo + ".\n\n"
                + "Vence en 5 minutos. Si no fuiste tú, ignora este correo y cambia tu contraseña.\n");
        Runnable enviar = () -> {
            try {
                correo.send(mensaje);
                log.info("otp: correo entregado al servidor SMTP");
            } catch (MailException e) {
                log.warn("otp: el envío por SMTP falló ({})", e.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar.run();
                }
            });
        } else {
            enviar.run();
        }
    }
}
