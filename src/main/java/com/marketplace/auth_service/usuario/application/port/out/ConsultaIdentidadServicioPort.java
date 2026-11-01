package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.usuario.application.dto.ResultadoLoteServicio;
import com.marketplace.auth_service.usuario.application.dto.UsuarioServicioVista;
import java.util.List;

/** Puente hacia la consulta de identidad para módulos consumidores (SPEC-18, identidad de Sergio). */
public interface ConsultaIdentidadServicioPort {
    UsuarioServicioVista obtener(ActorServicio actor, String idTexto);

    /** {@code POST /usuarios/lote} (SPEC-18 RF-18.2, FEAT-06-T2). */
    ResultadoLoteServicio obtenerLote(ActorServicio actor, List<String> idsTexto);
}
