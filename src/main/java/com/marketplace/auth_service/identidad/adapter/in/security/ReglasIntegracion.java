package com.marketplace.auth_service.identidad.adapter.in.security;

import com.marketplace.auth_service.shared.domain.ScopeServicio;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Reglas de acceso de las rutas de integración (SPEC-17 RF-17.8/17.9).
 *
 * <ul>
 *   <li>{@link #soloServicio}: exige un token de servicio con el scope. Un acceso
 *       de persona, o un servicio sin ese scope, recibe 403
 *       {@code SCOPE_INSUFICIENTE} sin decir qué faltaba.</li>
 *   <li>{@link #servicioOPersona}: el endpoint compartido con la administración
 *       (p. ej. {@code GET /usuarios/{id}}): un servicio necesita el scope; una
 *       persona entra y su caso de uso revalida el permiso con la base.</li>
 * </ul>
 * Sin token o con uno inválido nunca se llega aquí: el filtro ya respondió 401.
 */
public final class ReglasIntegracion {

    private ReglasIntegracion() {
    }

    public static AuthorizationManager<RequestAuthorizationContext> soloServicio(ScopeServicio scope) {
        return (autenticacion, contexto) -> new AuthorizationDecision(
                autenticacion.get() instanceof ServicioAutenticado servicio && servicio.getPrincipal().tiene(scope));
    }

    public static AuthorizationManager<RequestAuthorizationContext> servicioOPersona(ScopeServicio scope) {
        return (autenticacion, contexto) -> new AuthorizationDecision(
                autenticacion.get() instanceof ActorAutenticado
                        || (autenticacion.get() instanceof ServicioAutenticado servicio
                            && servicio.getPrincipal().tiene(scope)));
    }
}
