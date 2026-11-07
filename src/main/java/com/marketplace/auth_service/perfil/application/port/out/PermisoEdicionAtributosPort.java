package com.marketplace.auth_service.perfil.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorUsuario;

/** ¿El actor tiene hoy {@code atributos.editar}? Se revalida contra la base, nunca contra el token. */
public interface PermisoEdicionAtributosPort {
    boolean puedeEditarAtributos(ActorUsuario actor);
}
