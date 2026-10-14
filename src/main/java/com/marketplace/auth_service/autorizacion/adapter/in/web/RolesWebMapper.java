package com.marketplace.auth_service.autorizacion.adapter.in.web;

import com.marketplace.auth_service.autorizacion.adapter.in.web.dto.AsignarRolRequest;
import com.marketplace.auth_service.autorizacion.adapter.in.web.dto.UsuarioRolesResponse;
import com.marketplace.auth_service.autorizacion.application.dto.CambioRolCommand;
import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiados;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.util.UUID;

/** HTTP ↔ aplicación para la gestión de roles. Sin lógica ni consultas. */
final class RolesWebMapper {

    private RolesWebMapper() {
    }

    static CambioRolCommand aComando(ActorUsuario actor, UUID usuarioId, AsignarRolRequest cuerpo,
                                     ContextoPeticion contexto) {
        return new CambioRolCommand(actor, usuarioId, cuerpo == null ? null : cuerpo.rol(), contexto);
    }

    static UsuarioRolesResponse aRespuesta(RolesCambiados resultado) {
        var cuenta = resultado.cuenta();
        return new UsuarioRolesResponse(cuenta.id(), cuenta.nombreCompleto(), cuenta.correo(), cuenta.estado(),
                resultado.roles().stream().map(Rol::name).toList());
    }
}
