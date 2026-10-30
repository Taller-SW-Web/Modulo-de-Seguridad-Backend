package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.perfil.application.port.out.NotificacionesCorreoPort;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Puente a los tokens de un uso (Juan José) y al outbox (Christian). El token {@code CAMBIO_CORREO}
 * queda ligado a la dirección nueva; el enlace solo se persiste cifrado dentro del correo. La
 * confirmación reutiliza la ruta {@code /verificar-correo} de la SPA (RF-16.6).
 */
public final class NotificacionesCorreoAdapter implements NotificacionesCorreoPort {

    private final TokensUnUsoPort tokens;
    private final MensajeriaPort mensajeria;
    private final String urlSpa;
    private final Clock reloj;

    public NotificacionesCorreoAdapter(TokensUnUsoPort tokens, MensajeriaPort mensajeria, String urlSpa, Clock reloj) {
        this.tokens = Objects.requireNonNull(tokens);
        this.mensajeria = Objects.requireNonNull(mensajeria);
        this.urlSpa = Objects.requireNonNull(urlSpa).replaceAll("/+$", "");
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public void enviarConfirmacion(UUID usuarioId, String correoNuevo) {
        var emitido = tokens.emitirCambioCorreo(usuarioId, correoNuevo);
        String enlace = urlSpa + "/verificar-correo?token=" + URLEncoder.encode(emitido.tokenClaro(), StandardCharsets.UTF_8);
        mensajeria.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "correo.cambio_correo", correoNuevo,
                "Recibimos una solicitud para usar esta dirección como correo de tu cuenta.\n\n"
                        + "Confirma el cambio con este enlace (vence en " + vigencia(
                                Duration.between(reloj.instant(), emitido.expiraEn())) + "):\n" + enlace
                        + "\n\nSi no lo solicitaste, ignora este mensaje: tu correo no cambiará.\n"));
    }

    @Override
    public void avisarCorreoAnterior(String correoActual, String correoNuevo) {
        mensajeria.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "correo.cambio_correo_aviso", correoActual,
                "Se solicitó cambiar el correo de tu cuenta a " + enmascarar(correoNuevo) + ".\n\n"
                        + "Hasta que se confirme, seguirás entrando con esta dirección. "
                        + "Si no fuiste tú, cambia tu contraseña y contacta con soporte.\n"));
    }

    /** La vigencia que se anuncia sale del token emitido, no de un valor copiado aquí que pueda desfasarse. */
    static String vigencia(Duration restante) {
        long minutos = Math.max(1, Math.round(restante.toMillis() / 60_000.0));
        if (minutos < 60) return minutos + (minutos == 1 ? " minuto" : " minutos");
        long horas = Math.round(minutos / 60.0);
        return horas + (horas == 1 ? " hora" : " horas");
    }

    /** {@code nuevo@dominio.com} → {@code n***@dominio.com}: el aviso no entrega la dirección completa. */
    static String enmascarar(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 0) return "***";
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }
}
