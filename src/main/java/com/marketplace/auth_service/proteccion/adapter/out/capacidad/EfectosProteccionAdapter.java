package com.marketplace.auth_service.proteccion.adapter.out.capacidad;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.proteccion.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.proteccion.application.dto.EnlaceEmitido;
import com.marketplace.auth_service.proteccion.application.port.out.EfectosProteccionPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Efectos críticos de bloqueo/desbloqueo en el mismo commit (SPEC-14/15): auditoría crítica,
 * evento público y correo al titular con el enlace de desbloqueo, ambos por el outbox (el
 * correo, cifrado). El enlace solo va dentro del correo cifrado, nunca en auditoría ni eventos.
 */
public final class EfectosProteccionAdapter implements EfectosProteccionPort {

    private final AuditoriaPort auditoria;
    private final MensajeriaPort mensajeria;
    private final String urlSpa;

    public EfectosProteccionAdapter(AuditoriaPort auditoria, MensajeriaPort mensajeria, String urlSpa) {
        this.auditoria = Objects.requireNonNull(auditoria);
        this.mensajeria = Objects.requireNonNull(mensajeria);
        this.urlSpa = Objects.requireNonNull(urlSpa).replaceAll("/+$", "");
    }

    @Override
    public void bloqueada(CuentaSnapshot cuenta, UUID revision, Instant hasta, EnlaceEmitido enlace,
                          ContextoPeticion contexto, Instant ahora) {
        Map<String, String> detalle = new HashMap<>(Map.of("tipo", "AUTOMATICO"));
        detalle.put("hasta", hasta == null ? "sin vencimiento" : hasta.toString());
        auditoria.registrarCritica(new RegistroSeguro(UUID.randomUUID(), "CUENTA_BLOQUEADA", "EXITO", "SISTEMA", null,
                cuenta.usuarioId(), ahora, contexto.ip(), contexto.agente(), detalle));
        Map<String, String> carga = new HashMap<>(Map.of("usuarioId", cuenta.usuarioId().toString(), "automatico", "true",
                "fecha", ahora.toString()));
        if (hasta != null) {
            carga.put("hasta", hasta.toString());
        }
        mensajeria.guardarEvento(new EventoUsuario(UUID.randomUUID(), "usuario.bloqueado", carga));
        String enlaceUrl = urlSpa + "/desbloquear?token=" + URLEncoder.encode(enlace.secreto(), StandardCharsets.UTF_8);
        mensajeria.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "correo.cuenta_bloqueada", cuenta.correo(),
                "Detectamos varios intentos fallidos de inicio de sesión y bloqueamos tu cuenta por seguridad.\n\n"
                        + (hasta == null ? "El bloqueo no vence solo." : "El bloqueo vence a las " + hasta + " (UTC).")
                        + "\n\nSi fuiste tú, puedes desbloquearla ahora con este enlace (vence en 30 minutos):\n"
                        + enlaceUrl + "\n\nSi no fuiste tú, cambia tu contraseña al volver a entrar.\n"));
    }

    @Override
    public void desbloqueada(CuentaSnapshot cuenta, UUID revision, ContextoPeticion contexto, Instant ahora) {
        auditoria.registrarCritica(new RegistroSeguro(UUID.randomUUID(), "CUENTA_DESBLOQUEADA", "EXITO", "USUARIO",
                cuenta.usuarioId().toString(), cuenta.usuarioId(), ahora, contexto.ip(), contexto.agente(),
                Map.of("via", "ENLACE")));
        mensajeria.guardarEvento(new EventoUsuario(UUID.randomUUID(), "usuario.desbloqueado",
                Map.of("usuarioId", cuenta.usuarioId().toString(), "via", "ENLACE", "fecha", ahora.toString())));
        mensajeria.guardarCorreo(new EntregaCorreo(UUID.randomUUID(), "correo.cuenta_desbloqueada", cuenta.correo(),
                "Tu cuenta fue desbloqueada y ya puedes iniciar sesión.\n\nSi no fuiste tú, cambia tu contraseña.\n"));
    }

    @Override
    public void desbloqueadaPorRestablecimiento(CuentaSnapshot cuenta, UUID revision,
                                               ContextoPeticion contexto, Instant ahora) {
        auditoria.registrarCritica(new RegistroSeguro(UUID.randomUUID(), "CUENTA_DESBLOQUEADA", "EXITO", "USUARIO",
                cuenta.usuarioId().toString(), cuenta.usuarioId(), ahora, contexto.ip(), contexto.agente(),
                Map.of("via", "RESTABLECIMIENTO")));
        mensajeria.guardarEvento(new EventoUsuario(UUID.randomUUID(), "usuario.desbloqueado",
                Map.of("usuarioId", cuenta.usuarioId().toString(), "via", "RESTABLECIMIENTO", "fecha", ahora.toString())));
    }
}
