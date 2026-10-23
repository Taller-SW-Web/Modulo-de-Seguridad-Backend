package com.marketplace.auth_service.auditoria.adapter.out.correo;

import com.marketplace.auth_service.auditoria.application.port.out.EnvioCorreoPort;
import java.util.Objects;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** SMTP real (spring.mail.*); el contenido nunca se registra. Sin SMTP lanza y el worker reintenta. */
public final class EnvioCorreoSmtp implements EnvioCorreoPort {
    private final JavaMailSender correo;
    private final String remitente;

    public EnvioCorreoSmtp(JavaMailSender correo, String remitente) {
        this.correo = correo;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String destino, String asunto, String cuerpo) {
        if (correo == null || remitente == null || remitente.isBlank()) {
            throw new IllegalStateException("SMTP no configurado");
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(remitente);
        m.setTo(Objects.requireNonNull(destino));
        m.setSubject(asunto);
        m.setText(cuerpo);
        correo.send(m);
    }
}
