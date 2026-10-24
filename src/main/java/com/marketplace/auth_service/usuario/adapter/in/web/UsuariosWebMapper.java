package com.marketplace.auth_service.usuario.adapter.in.web;

import com.marketplace.auth_service.usuario.adapter.in.web.dto.PaginaUsuariosResponse;
import com.marketplace.auth_service.usuario.adapter.in.web.dto.UsuarioResponse;
import com.marketplace.auth_service.usuario.adapter.in.web.dto.UsuarioServicioResponse;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.dto.UsuarioServicioVista;
import com.marketplace.auth_service.usuario.application.dto.UsuarioVista;

/** Aplicación → HTTP para la consulta de cuentas. Sin lógica ni consultas. */
final class UsuariosWebMapper {

    private UsuariosWebMapper() {
    }

    /** SPEC-18: proyección de identidad para un módulo consumidor (servicio). */
    static UsuarioServicioResponse aRespuestaServicio(UsuarioServicioVista v) {
        return new UsuarioServicioResponse(v.id(), v.nombres(), v.apellidos(), v.nombreCompleto(), v.correo(),
                v.celular(), v.estado(), v.correoVerificado(), v.celularVerificado(), v.roles(), v.tipoDocumento(),
                v.documento(), v.documentoEnmascarado());
    }

    static UsuarioResponse aRespuesta(UsuarioVista u) {
        return new UsuarioResponse(u.id(), u.nombres(), u.apellidos(), u.nombreCompleto(), u.correo(), u.celular(),
                u.estado(), u.correoVerificado(), u.celularVerificado(), u.mfaHabilitado(), u.roles(),
                u.bloqueo() == null ? null
                        : new UsuarioResponse.BloqueoResponse(u.bloqueo().tipo(), u.bloqueo().hasta(), u.bloqueo().motivo()));
    }

    static PaginaUsuariosResponse aRespuesta(PaginaResultado<UsuarioVista> pagina) {
        return new PaginaUsuariosResponse(pagina.elementos().stream().map(UsuariosWebMapper::aRespuesta).toList(),
                pagina.pagina(), pagina.tamano(), pagina.total());
    }
}
