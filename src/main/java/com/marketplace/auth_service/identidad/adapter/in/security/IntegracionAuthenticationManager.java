package com.marketplace.auth_service.identidad.adapter.in.security;

import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.dto.ServicioValidado;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;
import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import java.util.Set;

/**
 * Gestor de las rutas de integración (SPEC-17/18): acepta un acceso de persona
 * <b>o</b> un token de servicio dirigido a esta API. Primero intenta el perfil de
 * persona; si el token resulta ser de servicio, lo valida con su propio perfil.
 *
 * <p>La autenticación decide solo quién llama; qué scope exige cada ruta lo
 * decide su regla de acceso ({@link ReglasIntegracion}). Los errores son los del
 * filtro de persona: 401 {@code TOKEN_INVALIDO} o 503 {@code NO_DISPONIBLE}.
 * El resto de rutas sigue con {@link AccesoAuthenticationManager}, que rechaza
 * el token de servicio con 403 {@code TOKEN_NO_APLICABLE} (ESC-17.4).
 */
public class IntegracionAuthenticationManager implements AuthenticationManager {

    private static final Logger log = LoggerFactory.getLogger(IntegracionAuthenticationManager.class);

    private final FirmaPort firmaPort;

    public IntegracionAuthenticationManager(FirmaPort firmaPort) {
        this.firmaPort = firmaPort;
    }

    @Override
    public Authentication authenticate(Authentication autenticacion) {
        if (!(autenticacion instanceof BearerTokenAuthenticationToken bearer)) {
            return null;
        }
        try {
            try {
                AccesoValidado acceso = firmaPort.validar(bearer.getToken());
                return new ActorAutenticado(new ActorUsuario(acceso.sub(), acceso.jti(),
                        Set.copyOf(acceso.roles()), Set.copyOf(acceso.permisos())));
            } catch (TokenNoAplicableException esDeServicio) {
                ServicioValidado servicio = firmaPort.validarServicio(bearer.getToken());
                return new ServicioAutenticado(new ActorServicio(servicio.clientId(), servicio.jti(),
                        servicio.scopes()));
            }
        } catch (TokenInvalidoException | TokenNoAplicableException e) {
            log.debug("token de integración rechazado: {}", e.getMessage());
            throw new InvalidBearerTokenException("Token ausente o inválido");
        } catch (FirmaNoDisponibleException e) {
            throw new AuthenticationServiceException("validación de tokens no disponible");
        }
    }
}
