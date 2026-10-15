package com.marketplace.auth_service.sesion.application.dto;

import com.marketplace.auth_service.shared.domain.Rol;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Roles y permisos vigentes para firmar y decidir MFA/caducidad (Sergio). */
public record RolesSesion(Set<Rol> roles, List<String> permisos) {
    public RolesSesion {
        Objects.requireNonNull(roles, "roles");
        roles = Collections.unmodifiableSet(roles.isEmpty() ? EnumSet.noneOf(Rol.class) : EnumSet.copyOf(roles));
        permisos = List.copyOf(Objects.requireNonNull(permisos, "permisos"));
    }

    /** Códigos ordenados para los claims del acceso. */
    public List<String> codigosRoles() {
        return roles.stream().map(Rol::name).sorted().toList();
    }
}
