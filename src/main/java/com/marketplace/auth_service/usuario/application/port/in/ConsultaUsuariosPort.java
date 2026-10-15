package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.usuario.application.dto.ConsultaUsuariosQuery;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.dto.UsuarioVista;

/**
 * Consulta administrativa de cuentas (SPEC-03 RF-03.2): listado con filtros
 * (EP-01-T003) y detalle (EP-01-T004). Ambas exigen {@code usuario.ver},
 * revalidado con los permisos actuales de la base antes de leer nada.
 */
public interface ConsultaUsuariosPort {

    /**
     * Página de cuentas filtrada por estado efectivo, rol y fragmento de correo.
     * Los filtros se aplican antes de contar y paginar.
     */
    PaginaResultado<UsuarioVista> listar(ActorUsuario actor, ConsultaUsuariosQuery consulta);

    /**
     * Detalle de una cuenta. {@code idTexto} llega como texto para validarlo
     * después de autorizar: sin permiso, la respuesta no revela si el objetivo existe.
     */
    UsuarioVista obtener(ActorUsuario actor, String idTexto);
}
