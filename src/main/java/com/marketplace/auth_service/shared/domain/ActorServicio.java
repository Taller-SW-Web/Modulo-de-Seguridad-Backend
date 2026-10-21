package com.marketplace.auth_service.shared.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Módulo consumidor que hace la petición, tal como lo acredita un token de
 * servicio <b>ya verificado</b> para esta API (SPEC-17). Identifica a un
 * módulo, no a una persona: no tiene roles ni permisos y nunca sustituye a un
 * {@link ActorUsuario} (ESC-17.4).
 *
 * <p>A diferencia de los permisos de persona, los scopes del token sí autorizan:
 * se conceden por escrito y el token vive como mucho una hora, sin refresco.
 *
 * @param clientId claim {@code sub}: el {@code client_id} del módulo
 * @param jti      claim {@code jti}
 * @param scopes   scopes de esta API presentes en el claim {@code scope}
 */
public record ActorServicio(String clientId, UUID jti, Set<String> scopes) {

    public ActorServicio {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId es obligatorio");
        }
        Objects.requireNonNull(jti, "jti");
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes"));
    }

    public boolean tiene(ScopeServicio scope) {
        return scopes.contains(scope.codigo());
    }
}
