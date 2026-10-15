package com.marketplace.auth_service.usuario.adapter.in.web;

import com.marketplace.auth_service.usuario.adapter.in.web.dto.PaginaUsuariosResponse;
import com.marketplace.auth_service.usuario.adapter.in.web.dto.UsuarioResponse;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.dto.UsuarioVista;

/** Aplicación → HTTP para la consulta de cuentas. Sin lógica ni consultas. */
final class UsuariosWebMapper {

    private UsuariosWebMapper() {
    }

    static UsuarioResponse aRespuesta(UsuarioVista u) {
        return new UsuarioResponse(u.id(), u.nombres(), u.apellidos(), u.nombreCompleto(), u.correo(), u.celular(),
                u.estado(), u.correoVerificado(), u.celularVerificado(), u.mfaHabilitado(), u.roles());
    }

    static PaginaUsuariosResponse aRespuesta(PaginaResultado<UsuarioVista> pagina) {
        return new PaginaUsuariosResponse(pagina.elementos().stream().map(UsuariosWebMapper::aRespuesta).toList(),
                pagina.pagina(), pagina.tamano(), pagina.total());
    }
}
