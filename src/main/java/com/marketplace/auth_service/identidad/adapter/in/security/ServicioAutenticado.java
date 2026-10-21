package com.marketplace.auth_service.identidad.adapter.in.security;

import com.marketplace.auth_service.shared.domain.ActorServicio;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

/**
 * Autenticación de Spring para un token de servicio ya validado (SPEC-17). El
 * principal es el {@link ActorServicio}; un controlador lo recibe con
 * {@code @AuthenticationPrincipal ActorServicio actor}. Sin authorities: los
 * scopes los exige la regla de cada ruta. Nunca guarda el token.
 */
public class ServicioAutenticado extends AbstractAuthenticationToken {

    private final ActorServicio actor;

    public ServicioAutenticado(ActorServicio actor) {
        super(List.of());
        this.actor = actor;
        setAuthenticated(true);
    }

    @Override
    public ActorServicio getPrincipal() {
        return actor;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
