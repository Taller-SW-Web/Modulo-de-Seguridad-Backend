package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.usuario.application.port.out.CorreoActivacionPort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puente del enlace de activación (SPEC-03 RF-03.5) hacia tokens de un uso (Juan José) y el outbox
 * (Christian). El enlace lleva a {@code /activar-cuenta} de nuestra SPA y solo va dentro del correo
 * cifrado, nunca en logs, auditoría ni eventos.
 */
public final class CorreoActivacionAdapter implements CorreoActivacionPort {

    private static final Logger log = LoggerFactory.getLogger(CorreoActivacionAdapter.class);

    /** RF-03.5: 72 horas. */
    static final Duration VIGENCIA = Duration.ofHours(72);

    private final TokensUnUsoPort tokens;
    private final MensajeriaPort mensajeria;
    private final String urlSpa;

    public CorreoActivacionAdapter(TokensUnUsoPort tokens, MensajeriaPort mensajeria, String urlSpa) {
        this.tokens = Objects.requireNonNull(tokens);
        this.mensajeria = Objects.requireNonNull(mensajeria);
        this.urlSpa = Objects.requireNonNull(urlSpa).replaceAll("/+$", "");
    }

    @Override
    public void enviar(UUID usuarioId, String correo, String nombres) {
        var token = tokens.emitir(usuarioId, PropositoToken.ACTIVACION, VIGENCIA);
        String enlace = urlSpa + "/activar-cuenta?token="
                + URLEncoder.encode(token.tokenClaro(), StandardCharsets.UTF_8);
        try {
            mensajeria.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "correo.activacion_cuenta", correo,
                    "Hola, " + nombres + ":\n\nSe creó una cuenta para ti en el marketplace. Para empezar a "
                            + "usarla, define tu contraseña y acepta los términos con este enlace "
                            + "(vence en 72 horas):\n" + enlace
                            + "\n\nSi no esperabas este mensaje, ignóralo: la cuenta no se activará.\n"));
        } catch (RuntimeException colaCaida) {
            log.warn("No se pudo encolar el correo de activación de la cuenta {}", usuarioId, colaCaida);
            throw new VerificacionNoDisponibleException(colaCaida);
        }
    }
}
