package com.marketplace.auth_service.identidad.adapter.in.security;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

/**
 * Autenticación de Spring para un acceso de persona ya validado. El principal es
 * el {@link ActorUsuario}; un controlador lo recibe con
 * {@code @AuthenticationPrincipal ActorUsuario actor}.
 *
 * <p>No lleva authorities: los roles y permisos del token no autorizan. Cada
 * operación decide con {@code AutorizacionPort} y los permisos actuales.
 * Nunca guarda el token.
 */
public class ActorAutenticado extends AbstractAuthenticationToken {

    private final ActorUsuario actor;

    public ActorAutenticado(ActorUsuario actor) {
        super(List.of());
        this.actor = actor;
        setAuthenticated(true);
    }

    @Override
    public ActorUsuario getPrincipal() {
        return actor;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
