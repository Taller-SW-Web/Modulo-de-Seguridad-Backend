package com.marketplace.auth_service.shared.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Persona que hace la petición, tal como la acredita un acceso <b>ya verificado</b>
 * (ACUERDOS-INTERNOS §2, tipo común). Solo lo construye el filtro de acceso
 * (TEC-IDENTIDAD-T002) a partir de un JWT válido; ningún cuerpo HTTP decide el actor.
 *
 * <p>{@code roles} y {@code permisos} son los claims del token: sirven para la
 * interfaz, pero <b>no autorizan</b>. Una operación protegida revalida los
 * permisos actuales con {@code AutorizacionPort}, porque pudieron cambiar
 * después de emitirse el token.
 *
 * @param sub      UUID de la persona (claim {@code sub})
 * @param jti      identificador del acceso (claim {@code jti})
 * @param roles    claim {@code roles}
 * @param permisos claim {@code permisos}
 */
public record ActorUsuario(UUID sub, UUID jti, Set<String> roles, Set<String> permisos) {

    public ActorUsuario {
        Objects.requireNonNull(sub, "sub");
        Objects.requireNonNull(jti, "jti");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        permisos = Set.copyOf(Objects.requireNonNull(permisos, "permisos"));
    }

    /** ¿La operación recae sobre la propia cuenta del actor? */
    public boolean esTitularDe(UUID usuarioId) {
        return sub.equals(usuarioId);
    }
}
