package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.ResultadoSolicitudCorreo;
import com.marketplace.auth_service.perfil.application.dto.SolicitudCambioCorreo;
import com.marketplace.auth_service.perfil.application.port.in.CambioCorreoPort;
import com.marketplace.auth_service.perfil.application.port.out.CredencialesCorreoPort;
import com.marketplace.auth_service.perfil.application.port.out.CuentaCorreoPort;
import com.marketplace.auth_service.perfil.application.port.out.EfectosCambioCorreoPort;
import com.marketplace.auth_service.perfil.application.port.out.NotificacionesCorreoPort;
import com.marketplace.auth_service.perfil.domain.exception.CambioCorreoNoPermitidaException;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Solicitud de cambio de correo (SPEC-16, FEAT-04-T3). Orden: titular → contraseña actual (Argon2id,
 * fuera de locks) → en la transacción: cuenta bloqueada, credencial sin cambios, correo libre → token
 * y correos. No toca {@code usuario.correo}: el cambio se aplica al confirmar el enlace.
 *
 * <p>Sin la contraseña no se emite nada (ESC-16.4). La contraseña no se registra en ningún lado.
 */
public final class CambioCorreoService implements CambioCorreoPort {

    private final CredencialesCorreoPort credenciales;
    private final CuentaCorreoPort cuentas;
    private final NotificacionesCorreoPort notificaciones;
    private final EfectosCambioCorreoPort efectos;

    public CambioCorreoService(CredencialesCorreoPort credenciales, CuentaCorreoPort cuentas,
                               NotificacionesCorreoPort notificaciones, EfectosCambioCorreoPort efectos) {
        this.credenciales = Objects.requireNonNull(credenciales);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.notificaciones = Objects.requireNonNull(notificaciones);
        this.efectos = Objects.requireNonNull(efectos);
    }

    @Override
    public ResultadoSolicitudCorreo solicitar(ActorUsuario actor, UUID usuarioId, SolicitudCambioCorreo solicitud,
                                              ContextoPeticion contexto) {
        Objects.requireNonNull(solicitud);
        if (actor == null || usuarioId == null || !actor.esTitularDe(usuarioId)) {
            throw new CambioCorreoNoPermitidaException();
        }
        String nuevo = normalizar(solicitud.nuevoCorreo());
        if (nuevo.isEmpty() || solicitud.contrasenaActual() == null || solicitud.contrasenaActual().isEmpty()) {
            throw new IllegalArgumentException("Faltan datos de la solicitud");
        }
        // Una cuenta que ya no existe recibe el mismo rechazo que otra cuenta.
        if (cuentas.leer(usuarioId).isEmpty()) throw new CambioCorreoNoPermitidaException();

        var revision = credenciales.preverificar(usuarioId, solicitud.contrasenaActual());
        if (revision.isEmpty()) return ResultadoSolicitudCorreo.CONTRASENA_INCORRECTA;

        return efectos.enTransaccion(() -> {
            cuentas.bloquear(usuarioId);
            if (!credenciales.confirmarRevision(revision.get())) {
                return ResultadoSolicitudCorreo.CONTRASENA_INCORRECTA; // cambió mientras tanto
            }
            // Se relee con la cuenta bloqueada: el correo actual pudo cambiar desde la lectura previa.
            var cuenta = cuentas.leer(usuarioId).orElseThrow(CambioCorreoNoPermitidaException::new);
            if (nuevo.equals(normalizar(cuenta.correo())) || cuentas.correoEnUso(nuevo)) {
                return ResultadoSolicitudCorreo.CORREO_NO_DISPONIBLE;
            }
            notificaciones.enviarConfirmacion(usuarioId, nuevo);
            notificaciones.avisarCorreoAnterior(cuenta.correo(), nuevo);
            return ResultadoSolicitudCorreo.ENVIADA;
        });
    }

    private static String normalizar(String correo) {
        return correo == null ? "" : correo.strip().toLowerCase(Locale.ROOT);
    }
}
