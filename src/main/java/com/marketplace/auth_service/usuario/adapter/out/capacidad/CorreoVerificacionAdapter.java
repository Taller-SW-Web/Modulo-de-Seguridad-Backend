package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.usuario.application.port.out.CorreoVerificacionPort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import com.marketplace.auth_service.usuario.domain.model.CanalOrigen;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puente de la verificación de correo hacia tokens de un uso (Juan José) y el outbox (Christian).
 * El enlace solo va dentro del correo cifrado, nunca en logs, auditoría ni eventos.
 *
 * <p>Todos los canales llevan por ahora a {@code /verificar-correo} de nuestra SPA (FEAT-01-T2);
 * la pantalla propia de CHATBOT de RF-02.8 queda pendiente. Fuera de {@code WEB}, el enlace añade
 * {@code &canal=} con el nombre del canal (lista cerrada, nunca una URL): la SPA lo traduce a su
 * login de retorno permitido por entorno (FEAT-01-T4, FRONT-03).
 */
public final class CorreoVerificacionAdapter implements CorreoVerificacionPort {

    private static final Logger log = LoggerFactory.getLogger(CorreoVerificacionAdapter.class);

    /** RF-02.1: 24 horas. Los tokens rechazan otra vigencia para VERIFICAR_CORREO. */
    static final Duration VIGENCIA = Duration.ofHours(24);

    private final TokensUnUsoPort tokens;
    private final MensajeriaPort mensajeria;
    private final String urlSpa;

    public CorreoVerificacionAdapter(TokensUnUsoPort tokens, MensajeriaPort mensajeria, String urlSpa) {
        this.tokens = Objects.requireNonNull(tokens);
        this.mensajeria = Objects.requireNonNull(mensajeria);
        this.urlSpa = Objects.requireNonNull(urlSpa).replaceAll("/+$", "");
    }

    @Override
    public void enviar(UUID usuarioId, String correo, String nombres, CanalOrigen canalOrigen) {
        var token = tokens.emitir(usuarioId, PropositoToken.VERIFICAR_CORREO, VIGENCIA);
        String enlace = urlSpa + "/verificar-correo?token="
                + URLEncoder.encode(token.tokenClaro(), StandardCharsets.UTF_8)
                + (canalOrigen == CanalOrigen.WEB ? "" : "&canal=" + canalOrigen.name());
        try {
            mensajeria.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "correo.verificacion_correo", correo,
                    "Hola, " + nombres + ":\n\nGracias por registrarte. Confirma tu correo con este enlace "
                            + "(vence en 24 horas):\n" + enlace
                            + "\n\nSi no creaste esta cuenta, ignora este mensaje.\n"));
        } catch (RuntimeException colaCaida) {
            // Único registro del fallo cuando el reenvío lo absorbe con 202 para no delatar la cuenta.
            log.warn("No se pudo encolar el correo de verificación de la cuenta {}", usuarioId, colaCaida);
            throw new VerificacionNoDisponibleException(colaCaida);
        }
    }
}
