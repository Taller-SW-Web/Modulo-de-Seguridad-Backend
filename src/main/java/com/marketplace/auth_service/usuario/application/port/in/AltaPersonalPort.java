package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAltaPersonal;
import com.marketplace.auth_service.usuario.application.dto.SolicitudAltaPersonal;

/** Alta administrativa de personal (SPEC-03 RF-03.1, FEAT-13-T1). Abre sus propias transacciones. */
public interface AltaPersonalPort {

    /**
     * Comprueba {@code usuario.crear} con los permisos actuales antes de leer el cuerpo, para que
     * alguien sin permiso reciba 403 aunque la solicitud sea inválida. Si falta, registra
     * {@code ACCESO_DENEGADO} y devuelve {@code false}.
     */
    boolean autorizar(ActorUsuario actor, ContextoPeticion contexto);

    ResultadoAltaPersonal crear(ActorUsuario actor, SolicitudAltaPersonal solicitud, ContextoPeticion contexto);
}
