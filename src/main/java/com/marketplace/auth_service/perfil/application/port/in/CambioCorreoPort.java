package com.marketplace.auth_service.perfil.application.port.in;

import com.marketplace.auth_service.perfil.application.dto.ResultadoSolicitudCorreo;
import com.marketplace.auth_service.perfil.application.dto.SolicitudCambioCorreo;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.UUID;

/**
 * Solicitud de cambio de correo (SPEC-16, FEAT-04-T3). Solo el titular y con su contraseña actual.
 * El correo no cambia aquí: se aplica al confirmar el enlace enviado a la dirección nueva.
 */
public interface CambioCorreoPort {
    /** @throws com.marketplace.auth_service.perfil.domain.exception.CambioCorreoNoPermitidaException si el actor no es el titular */
    ResultadoSolicitudCorreo solicitar(ActorUsuario actor, UUID usuarioId, SolicitudCambioCorreo solicitud,
                                       ContextoPeticion contexto);
}
