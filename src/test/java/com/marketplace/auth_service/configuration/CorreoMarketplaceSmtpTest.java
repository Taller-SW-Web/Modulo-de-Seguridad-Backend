package com.marketplace.auth_service.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketplace.auth_service.auditoria.adapter.out.correo.EnvioCorreoSmtp;
import com.marketplace.auth_service.auditoria.adapter.out.crypto.CifradoMensajesAesGcm;
import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import com.marketplace.auth_service.auditoria.application.service.MensajeriaService;
import com.marketplace.auth_service.credencial.adapter.out.capacidad.CorreoRecuperacionAdapter;
import com.marketplace.auth_service.credencial.application.dto.CanalOrigenRecuperacion;
import com.marketplace.auth_service.credencial.application.dto.CuentaRecuperacion;
import com.marketplace.auth_service.credencial.application.dto.TokenEmitido;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.mfa.adapter.out.capacidad.EntregaOtpOutbox;
import com.marketplace.auth_service.mfa.application.port.out.EntregaOtpPort;
import com.marketplace.auth_service.mfa.domain.model.CanalOtp;
import com.marketplace.auth_service.perfil.SmtpLocalDePrueba;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.CorreoVerificacionAdapter;
import com.marketplace.auth_service.usuario.domain.model.CanalOrigen;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class CorreoMarketplaceSmtpTest {
    private static final String SPA = "https://seguridad.example.test";
    private static final String DESTINO = "cliente@example.test";
    private static final String TOKEN = "token+de/prueba";
    private static final String OTP = "012345";

    @ParameterizedTest
    @CsvSource({
        "verificar-correo, Verifica tu correo en Inka Athletics, Verificar mi correo, 24 horas",
        "recuperar-contrasena, Restablece tu contraseña de Inka Athletics, Restablecer mi contraseña, 30 minutos",
        "otp, Tu código para iniciar sesión, 012345, más reciente"
    })
    void entregaElCorreoConElEnlaceCorrectoYLogoPorSmtp(
            String ruta, String asunto, String boton, String vigencia) throws Exception {
        try (var smtp = SmtpLocalDePrueba.iniciar()) {
            var sender = new JavaMailSenderImpl();
            sender.setHost("127.0.0.1");
            sender.setPort(smtp.puerto());
            sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "3000");
            sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "3000");
            var outbox = mock(OutboxPort.class);
            var cifrado = new CifradoMensajesAesGcm("prueba", new byte[32]);
            var mensajeria = new MensajeriaService(outbox, cifrado,
                    new EnvioCorreoSmtp(sender, "seguridad@example.test"), Clock.systemUTC());
            var tokens = mock(TokensUnUsoPort.class);
            UUID usuario = UUID.randomUUID();
            Instant ahora = Instant.parse("2026-11-23T14:00:00Z");
            when(tokens.emitir(any(), any(), any()))
                    .thenReturn(new TokenEmitido(UUID.randomUUID(), TOKEN, ahora.plusSeconds(1800)));

            boolean otp = ruta.equals("otp");
            if (otp) {
                new EntregaOtpOutbox(mensajeria, mock(EntregaOtpPort.class)).programar(CanalOtp.EMAIL, DESTINO, OTP);
            } else if (ruta.equals("verificar-correo")) {
                new CorreoVerificacionAdapter(tokens, mensajeria, SPA, "")
                        .enviar(usuario, DESTINO, "Cliente de prueba", CanalOrigen.MARKETPLACE);
            } else {
                new CorreoRecuperacionAdapter(tokens, mensajeria, SPA)
                        .emitirYEnviar(new CuentaRecuperacion(usuario, DESTINO, true),
                                CanalOrigenRecuperacion.MARKETPLACE, ahora);
            }

            var captor = ArgumentCaptor.forClass(OutboxPort.Mensaje.class);
            verify(outbox).guardar(captor.capture());
            var fila = captor.getValue();
            String valor = otp ? OTP : SPA + "/" + ruta + "?token=token%2Bde%2Fprueba&canal=MARKETPLACE";
            assertThat(fila.payload().values()).noneMatch(v -> v.contains("token") || v.contains(DESTINO));
            assertThat(cifrado.descifrar(fila.payload().get("enlace"))).isEqualTo(valor);
            if (otp) {
                assertThat(fila.payload().values()).noneMatch(v -> v.equals(OTP));
            }
            when(outbox.reclamarCorreos(anyInt(), any(), any())).thenReturn(List.of(fila));

            assertThat(mensajeria.procesarCorreos()).isEqualTo(1);
            assertThat(smtp.recibidos()).hasSize(1);
            var recibido = smtp.recibidos().getFirst();
            assertThat(recibido.destinatarios()).containsExactly(DESTINO);
            var mensaje = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(recibido.datos()));
            assertThat(mensaje.getSubject()).isEqualTo(asunto);
            var cuerpo = (Multipart) mensaje.getContent();
            var alternativas = (Multipart) cuerpo.getBodyPart(0).getContent();
            assertThat(alternativas.getBodyPart(0).getContent().toString()).contains(valor, vigencia);
            assertThat(alternativas.getBodyPart(1).getContent().toString())
                    .contains(otp ? OTP : "href=\"" + valor.replace("&", "&amp;") + "\"",
                            boton, "cid:inka-athletics-logo")
                    .doesNotContain("{{");
            assertThat(cuerpo.getBodyPart(1).isMimeType("image/png")).isTrue();
            assertThat(cuerpo.getBodyPart(1).getHeader("Content-ID")).containsExactly("<inka-athletics-logo>");
        }
    }
}
