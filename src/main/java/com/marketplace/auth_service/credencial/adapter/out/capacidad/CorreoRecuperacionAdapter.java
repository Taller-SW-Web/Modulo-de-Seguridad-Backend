package com.marketplace.auth_service.credencial.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.application.dto.CuentaRecuperacion;
import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.credencial.application.port.out.CorreoRecuperacionPort;
import com.marketplace.auth_service.credencial.domain.exception.SolicitudRecuperacionNoDisponibleException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Puente hacia tokens y outbox: el enlace solo se persiste cifrado para entrega asíncrona. */
public final class CorreoRecuperacionAdapter implements CorreoRecuperacionPort {
    private final TokensUnUsoPort tokens;
    private final MensajeriaPort mensajeria;
    private final String urlSpa;

    public CorreoRecuperacionAdapter(TokensUnUsoPort tokens, MensajeriaPort mensajeria, String urlSpa) {
        this.tokens = Objects.requireNonNull(tokens);
        this.mensajeria = Objects.requireNonNull(mensajeria);
        this.urlSpa = Objects.requireNonNull(urlSpa).replaceAll("/+$", "");
    }

    @Override
    public void emitirYEnviar(CuentaRecuperacion cuenta, Instant ahora) {
        Objects.requireNonNull(cuenta);
        Objects.requireNonNull(ahora);
        var emitido = tokens.emitir(cuenta.usuarioId(), PropositoToken.RECUPERACION, Duration.ofMinutes(30));
        String enlace = urlSpa + "/recuperar-contrasena?token="
                + URLEncoder.encode(emitido.tokenClaro(), StandardCharsets.UTF_8);
        String contenido = "Recibimos una solicitud para recuperar la contraseña de tu cuenta.\n\n"
                + "Abre este enlace para crear una contraseña nueva (vence en 30 minutos):\n"
                + enlace + "\n\nSi no solicitaste este cambio, ignora este mensaje. "
                + "Si solicitas otro enlace, este dejará de funcionar.";
        try {
            mensajeria.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "recuperacion", cuenta.correo(), contenido));
        } catch (RuntimeException indisponible) {
            throw new SolicitudRecuperacionNoDisponibleException();
        }
    }
}
