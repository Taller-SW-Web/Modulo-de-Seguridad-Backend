package com.marketplace.auth_service.mfa.domain.model;

import java.util.Objects;
import java.util.Set;
import com.marketplace.auth_service.shared.domain.Rol;

/** Regla de segundo factor de RF-10.1; no administra el catálogo ni permisos. */
public final class PoliticaMfa {
    private PoliticaMfa() {}

    public static boolean obligatorio(Set<Rol> roles) {
        Objects.requireNonNull(roles, "roles");
        return Set.copyOf(roles).stream().anyMatch(Rol::esDeGestion);
    }

    public static boolean efectivo(boolean voluntario, Set<Rol> roles) {
        boolean obligatorio = obligatorio(roles);
        return voluntario || obligatorio;
    }
}
