package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.dto.TokenEmitido;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** FEAT-01-T2: token VERIFICAR_CORREO de 24 h y enlace a la SPA, solo dentro del correo encolado. */
class CorreoVerificacionAdapterTest {

    private static final UUID USUARIO = UUID.randomUUID();
    /** Base64url puede traer '-' y '_'; el '+' y '/' no, pero el enlace debe codificar igual. */
    private static final String TOKEN = "a+b/c=d_e-f";

    private final TokensUnUsoPort tokens = mock(TokensUnUsoPort.class);
    private final MensajeriaPort mensajeria = mock(MensajeriaPort.class);

    private EntregaCorreo enviar(String urlSpa) {
        when(tokens.emitir(USUARIO, PropositoToken.VERIFICAR_CORREO, Duration.ofHours(24)))
                .thenReturn(new TokenEmitido(UUID.randomUUID(), TOKEN, Instant.parse("2026-10-24T15:00:00Z")));
        new CorreoVerificacionAdapter(tokens, mensajeria, urlSpa).enviar(USUARIO, "maria@ejemplo.com", "María");
        var correo = ArgumentCaptor.forClass(EntregaCorreo.class);
        verify(mensajeria).guardarCorreo(correo.capture());
        return correo.getValue();
    }

    @Test
    void emiteTokenDeVerificacionDe24HorasYEncolaElEnlaceALaSpa() {
        var correo = enviar("https://g7-seguridad-spa.onrender.com");

        verify(tokens).emitir(USUARIO, PropositoToken.VERIFICAR_CORREO, Duration.ofHours(24));
        assertThat(correo.topico()).isEqualTo("correo.verificacion_correo");
        assertThat(correo.destino()).isEqualTo("maria@ejemplo.com");
        assertThat(correo.contenido())
                .contains("Hola, María")
                .contains("https://g7-seguridad-spa.onrender.com/verificar-correo?token=a%2Bb%2Fc%3Dd_e-f")
                .contains("24 horas")
                .doesNotContain("?token=" + TOKEN);
    }

    @Test
    void normalizaLaBarraFinalDeLaUrlDeLaSpa() {
        assertThat(enviar("http://localhost:5173//").contenido())
                .contains("http://localhost:5173/verificar-correo?token=");
    }

    @Test
    void colaDeCorreoCaidaSeTraduceEnVerificacionNoDisponible() {
        when(tokens.emitir(any(), any(), any(Duration.class)))
                .thenReturn(new TokenEmitido(UUID.randomUUID(), TOKEN, Instant.parse("2026-10-24T15:00:00Z")));
        doThrow(new IllegalStateException("sin clave")).when(mensajeria).guardarCorreo(any());
        var adaptador = new CorreoVerificacionAdapter(tokens, mensajeria, "http://localhost:5173");

        assertThatThrownBy(() -> adaptador.enviar(USUARIO, "maria@ejemplo.com", "María"))
                .isInstanceOf(VerificacionNoDisponibleException.class)
                .hasMessageNotContaining(TOKEN);
    }
}
