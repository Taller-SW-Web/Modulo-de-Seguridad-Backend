package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.application.dto.TokenEmitido;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Puente de FEAT-04-T3 hacia tokens de un uso y outbox: destino del token, enlace y aviso enmascarado. */
class NotificacionesCorreoAdapterTest {
    private final TokensUnUsoPort tokens = mock(TokensUnUsoPort.class);
    private final MensajeriaPort mensajeria = mock(MensajeriaPort.class);
    private static final Instant AHORA = Instant.parse("2026-10-24T15:00:00Z");
    private final NotificacionesCorreoAdapter adaptador = new NotificacionesCorreoAdapter(tokens, mensajeria,
            "https://g7.ejemplo.test/", Clock.fixed(AHORA, ZoneOffset.UTC));
    private final UUID usuario = UUID.randomUUID();

    @Test
    void emiteElTokenLigadoALaDireccionNuevaYLoEnviaAEsaDireccion() {
        when(tokens.emitirCambioCorreo(any(), any()))
                .thenReturn(new TokenEmitido(UUID.randomUUID(), "tok+en/claro", AHORA.plus(Duration.ofHours(24))));

        adaptador.enviarConfirmacion(usuario, "nuevo@correo.com");

        verify(tokens).emitirCambioCorreo(usuario, "nuevo@correo.com");
        var captor = ArgumentCaptor.forClass(EntregaCorreo.class);
        verify(mensajeria).guardarCorreo(captor.capture());
        EntregaCorreo correo = captor.getValue();
        assertThat(correo.destino()).isEqualTo("nuevo@correo.com");
        assertThat(correo.topico()).startsWith("correo.");
        assertThat(correo.contenido())
                .contains("https://g7.ejemplo.test/verificar-correo?token=tok%2Ben%2Fclaro")
                .contains("vence en 24 horas");
    }

    @Test
    void laVigenciaAnunciadaSigueAlTokenEmitidoYNoAUnValorCopiado() {
        when(tokens.emitirCambioCorreo(any(), any()))
                .thenReturn(new TokenEmitido(UUID.randomUUID(), "tok", AHORA.plus(Duration.ofMinutes(30))));

        adaptador.enviarConfirmacion(usuario, "nuevo@correo.com");

        var captor = ArgumentCaptor.forClass(EntregaCorreo.class);
        verify(mensajeria).guardarCorreo(captor.capture());
        assertThat(captor.getValue().contenido()).contains("vence en 30 minutos").doesNotContain("24 horas");
    }

    @Test
    void vigenciaRedondeaAMinutosOHoras() {
        assertThat(NotificacionesCorreoAdapter.vigencia(Duration.ofSeconds(20))).isEqualTo("1 minuto");
        assertThat(NotificacionesCorreoAdapter.vigencia(Duration.ofMinutes(59))).isEqualTo("59 minutos");
        assertThat(NotificacionesCorreoAdapter.vigencia(Duration.ofMinutes(60))).isEqualTo("1 hora");
        assertThat(NotificacionesCorreoAdapter.vigencia(Duration.ofHours(24).minusSeconds(3))).isEqualTo("24 horas");
    }

    @Test
    void elAvisoALaDireccionAnteriorNoEntregaLaNuevaCompleta() {
        adaptador.avisarCorreoAnterior("actual@correo.com", "nuevo@correo.com");

        var captor = ArgumentCaptor.forClass(EntregaCorreo.class);
        verify(mensajeria).guardarCorreo(captor.capture());
        EntregaCorreo aviso = captor.getValue();
        assertThat(aviso.destino()).isEqualTo("actual@correo.com");
        assertThat(aviso.topico()).startsWith("correo.");
        assertThat(aviso.contenido()).contains("n***@correo.com").doesNotContain("nuevo@correo.com");
    }

    @Test
    void enmascararNoFallaConDireccionesRaras() {
        assertThat(NotificacionesCorreoAdapter.enmascarar("a@b.co")).isEqualTo("a***@b.co");
        assertThat(NotificacionesCorreoAdapter.enmascarar("@b.co")).isEqualTo("***");
        assertThat(NotificacionesCorreoAdapter.enmascarar("sin-arroba")).isEqualTo("***");
    }
}
