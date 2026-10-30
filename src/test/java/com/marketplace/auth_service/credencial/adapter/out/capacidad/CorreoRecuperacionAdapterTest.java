package com.marketplace.auth_service.credencial.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.application.dto.CanalOrigenRecuperacion;
import com.marketplace.auth_service.credencial.application.dto.CuentaRecuperacion;
import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.dto.TokenEmitido;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CorreoRecuperacionAdapterTest {
    private static final UUID USUARIO = UUID.fromString("41e96665-6429-44c4-b19b-f842bac83cbc");
    private static final Instant AHORA = Instant.parse("2026-10-30T12:00:00Z");
    private static final String TOKEN = "a+b/c=d_e-f";

    private final TokensUnUsoPort tokens = mock(TokensUnUsoPort.class);
    private final MensajeriaPort mensajeria = mock(MensajeriaPort.class);

    @Test
    void conservaMarketplaceEnElEnlaceSinAceptarUnaUrlDeRetorno() {
        var correo = emitir(CanalOrigenRecuperacion.MARKETPLACE);

        assertThat(correo.contenido())
                .contains("https://spa.example.test/recuperar-contrasena?token=a%2Bb%2Fc%3Dd_e-f&canal=MARKETPLACE")
                .doesNotContain("https://g1-marketplace-frontend.vercel.app", "retorno=");
        verify(tokens).emitir(USUARIO, PropositoToken.RECUPERACION, Duration.ofMinutes(30));
    }

    @Test
    void canalWebUsaElDestinoPredeterminadoSinParametroCanal() {
        var correo = emitir(CanalOrigenRecuperacion.WEB);

        assertThat(correo.contenido())
                .contains("/recuperar-contrasena?token=a%2Bb%2Fc%3Dd_e-f")
                .doesNotContain("&canal=");
    }

    @Test
    void conservaLosOtrosCanalesPublicadosEnElEnlace() {
        for (CanalOrigenRecuperacion canal : new CanalOrigenRecuperacion[]{
                CanalOrigenRecuperacion.CHATBOT, CanalOrigenRecuperacion.RETAIL}) {
            org.mockito.Mockito.clearInvocations(mensajeria);
            assertThat(emitir(canal).contenido()).contains("&canal=" + canal.name());
        }
    }

    private EntregaCorreo emitir(CanalOrigenRecuperacion canal) {
        when(tokens.emitir(USUARIO, PropositoToken.RECUPERACION, Duration.ofMinutes(30)))
                .thenReturn(new TokenEmitido(UUID.randomUUID(), TOKEN, AHORA.plus(Duration.ofMinutes(30))));
        new CorreoRecuperacionAdapter(tokens, mensajeria, "https://spa.example.test/")
                .emitirYEnviar(new CuentaRecuperacion(USUARIO, "persona@example.test", true), canal, AHORA);
        var captor = ArgumentCaptor.forClass(EntregaCorreo.class);
        verify(mensajeria).guardarCorreo(captor.capture());
        return captor.getValue();
    }
}
