package com.marketplace.auth_service.auditoria.adapter.out.correo;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo.Plantilla;
import com.marketplace.auth_service.auditoria.application.port.out.EnvioCorreoPort;
import jakarta.mail.MessagingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;

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
        comprobarConfiguracion();
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(remitente);
        m.setTo(Objects.requireNonNull(destino));
        m.setSubject(asunto);
        m.setText(cuerpo);
        correo.send(m);
    }

    @Override
    public void enviarConPlantilla(String destino, String asunto, String cuerpo, Plantilla plantilla) {
        comprobarConfiguracion();
        String logo = "inka-athletics-logo";
        String ruta = "correo/marketplace/";
        String archivo;
        String variable;
        String asuntoMarketplace;
        switch (plantilla.tipo()) {
            case VERIFICACION_MARKETPLACE -> {
                archivo = "verificacion-de-correo.html";
                variable = "{{verificationUrl}}";
                asuntoMarketplace = "Verifica tu correo en Inka Athletics";
            }
            case RECUPERACION_MARKETPLACE -> {
                archivo = "recuperacion-de-contrasena.html";
                variable = "{{resetUrl}}";
                asuntoMarketplace = "Restablece tu contraseña de Inka Athletics";
            }
            case OTP_INICIO_SESION -> {
                archivo = "otp-inicio-sesion.html";
                variable = "{{otp}}";
                asuntoMarketplace = "Tu código para iniciar sesión";
            }
            default -> throw new IllegalArgumentException("Plantilla de correo desconocida");
        }
        try {
            String html = new ClassPathResource(ruta + archivo).getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{logoUrl}}", "cid:" + logo)
                    .replace(variable, HtmlUtils.htmlEscape(plantilla.enlace(), StandardCharsets.UTF_8.name()));
            var mensaje = correo.createMimeMessage();
            var mime = new MimeMessageHelper(mensaje, MimeMessageHelper.MULTIPART_MODE_RELATED, "UTF-8");
            mime.setFrom(remitente);
            mime.setTo(destino);
            mime.setSubject(asuntoMarketplace);
            mime.setText(cuerpo, html);
            mime.addInline(logo, new ClassPathResource(ruta + "inka-athletics-logo-ink.png"), "image/png");
            correo.send(mensaje);
        } catch (MessagingException | IOException e) {
            throw new MailPreparationException("No se pudo preparar el correo de Marketplace", e);
        }
    }

    private void comprobarConfiguracion() {
        if (correo == null || remitente == null || remitente.isBlank()) {
            throw new IllegalStateException("SMTP no configurado");
        }
    }
}
