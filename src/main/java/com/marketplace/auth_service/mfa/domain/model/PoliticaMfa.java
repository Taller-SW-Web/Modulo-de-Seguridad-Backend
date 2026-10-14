package com.marketplace.auth_service.mfa.domain.model;

import java.util.Objects;
import java.util.Set;

/** Regla de segundo factor de RF-10.1; no administra el catálogo ni permisos. */
public final class PoliticaMfa {
    private static final Set<String> GESTION = Set.of("ADMIN_VENTAS", "GESTOR_DESPACHO",
            "GESTOR_COMERCIAL", "ADMIN_SISTEMA");
    private static final Set<String> CANONICOS = Set.of("CLIENTE", "VENDEDOR", "ADMIN_VENTAS",
            "GESTOR_DESPACHO", "GESTOR_COMERCIAL", "ADMIN_SISTEMA");
    private PoliticaMfa() {}

    public static boolean obligatorio(Set<String> roles) {
        Objects.requireNonNull(roles, "roles");
        // Error de composición/configuración: no tratar un rol desconocido como opcional.
        if (roles.stream().anyMatch(r -> r == null || !CANONICOS.contains(r))) {
            throw new IllegalArgumentException("Roles no canónicos en la política MFA");
        }
        return roles.stream().anyMatch(GESTION::contains);
    }

    public static boolean efectivo(boolean voluntario, Set<String> roles) {
        boolean obligatorio = obligatorio(roles);
        return voluntario || obligatorio;
    }
}
