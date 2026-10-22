package com.marketplace.auth_service.shared.domain;

import java.util.Optional;

/**
 * Scopes de servicio de la API de Seguridad (SPEC-17, tabla «Scopes del token
 * de servicio»). Todos llevan {@code aud: "api-seguridad"}. Se escriben
 * {@code recurso:accion}, con dos puntos: así nunca se confunden con un permiso
 * de persona, que usa punto.
 *
 * <p>Los scopes de APIs ajenas no aparecen aquí: los define su dueño, nosotros
 * solo los registramos en la tabla {@code scope} y los copiamos al token (A4).
 */
public enum ScopeServicio {
    TOKENS_INTROSPECCION("tokens:introspeccion"),
    USUARIOS_LEER("usuarios:leer"),
    USUARIOS_LEER_DOCUMENTO("usuarios:leer:documento"),
    DIRECCIONES_LEER("direcciones:leer"),
    ROLES_LEER("roles:leer");

    /** Audiencia de todos los scopes de esta API (RF-17.12). */
    public static final String AUDIENCIA = "api-seguridad";

    private final String codigo;

    ScopeServicio(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public static Optional<ScopeServicio> desdeCodigo(String codigo) {
        for (ScopeServicio scope : values()) {
            if (scope.codigo.equals(codigo)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }
}
