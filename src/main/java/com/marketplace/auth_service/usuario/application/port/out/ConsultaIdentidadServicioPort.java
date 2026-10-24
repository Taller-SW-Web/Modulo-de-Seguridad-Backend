package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.usuario.application.dto.UsuarioServicioVista;

/** Puente hacia la consulta de identidad para módulos consumidores (SPEC-18, identidad de Sergio). */
public interface ConsultaIdentidadServicioPort {
    UsuarioServicioVista obtener(ActorServicio actor, String idTexto);
}
