package com.marketplace.auth_service.identidad.adapter.in.security;

import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;
import com.marketplace.auth_service.identidad.domain.exception.TokenInvalidoException;
import com.marketplace.auth_service.identidad.domain.exception.TokenNoAplicableException;
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
 * Puente entre Spring Security y {@link FirmaPort#validar}: el filtro Bearer de
 * Spring extrae el token de la cabecera {@code Authorization} y este gestor lo
 * valida con el caso de uso, sin duplicar sus reglas.
 *
 * <ul>
 *   <li>Válido → {@link ActorAutenticado} con el {@link ActorUsuario}.</li>
 *   <li>Inválido → 401 {@code TOKEN_INVALIDO}. El motivo se registra en debug, nunca el token.</li>
 *   <li>Token de servicio → 403 {@code TOKEN_NO_APLICABLE}.</li>
 *   <li>Sin clave configurada → 503 {@code NO_DISPONIBLE}.</li>
 * </ul>
 */
public class AccesoAuthenticationManager implements AuthenticationManager {

    private static final Logger log = LoggerFactory.getLogger(AccesoAuthenticationManager.class);

    private final FirmaPort firmaPort;

    public AccesoAuthenticationManager(FirmaPort firmaPort) {
        this.firmaPort = firmaPort;
    }

    @Override
    public Authentication authenticate(Authentication autenticacion) {
        if (!(autenticacion instanceof BearerTokenAuthenticationToken bearer)) {
            return null;
        }
        try {
            AccesoValidado acceso = firmaPort.validar(bearer.getToken());
            return new ActorAutenticado(new ActorUsuario(acceso.sub(), acceso.jti(),
                    Set.copyOf(acceso.roles()), Set.copyOf(acceso.permisos())));
        } catch (TokenInvalidoException e) {
            log.debug("acceso rechazado: {}", e.getMessage());
            throw new InvalidBearerTokenException("Token ausente o inválido");
        } catch (TokenNoAplicableException e) {
            throw new TokenNoAplicableAuthenticationException();
        } catch (FirmaNoDisponibleException e) {
            throw new AuthenticationServiceException("validación de accesos no disponible");
        }
    }
}
