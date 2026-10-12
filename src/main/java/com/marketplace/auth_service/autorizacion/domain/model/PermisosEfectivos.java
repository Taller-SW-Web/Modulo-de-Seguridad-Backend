package com.marketplace.auth_service.autorizacion.domain.model;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Roles de una persona y la unión sin duplicados de sus permisos (RF-11.4).
 * Es lo que viaja en los claims {@code roles} y {@code permisos} del acceso.
 *
 * <p>Las listas de códigos salen en orden alfabético, así dos emisiones con los
 * mismos roles producen exactamente el mismo token (ESC-11.4:
 * {@code ["ADMIN_SISTEMA", "VENDEDOR"]}).
 */
public record PermisosEfectivos(Set<Rol> roles, Set<Permiso> permisos) {

    public PermisosEfectivos {
        roles = Collections.unmodifiableSet(roles.isEmpty() ? EnumSet.noneOf(Rol.class) : EnumSet.copyOf(roles));
        permisos = Collections.unmodifiableSet(
                permisos.isEmpty() ? EnumSet.noneOf(Permiso.class) : EnumSet.copyOf(permisos));
    }

    /** Persona sin roles: arrays vacíos, nunca null. */
    public static PermisosEfectivos ninguno() {
        return new PermisosEfectivos(Set.of(), Set.of());
    }

    /** Calcula la unión con la matriz del catálogo; útil cuando no se consulta la base. */
    public static PermisosEfectivos desdeCatalogo(Collection<Rol> roles) {
        Set<Permiso> union = EnumSet.noneOf(Permiso.class);
        roles.forEach(rol -> union.addAll(CatalogoAutorizacion.permisosDe(rol)));
        return new PermisosEfectivos(Set.copyOf(roles), union);
    }

    public boolean tiene(Permiso permiso) {
        return permisos.contains(permiso);
    }

    public boolean tiene(Rol rol) {
        return roles.contains(rol);
    }

    /** Claim {@code roles}: códigos ordenados alfabéticamente. */
    public List<String> codigosRoles() {
        return roles.stream().map(Rol::name).sorted().toList();
    }

    /** Claim {@code permisos}: códigos con punto, ordenados alfabéticamente. */
    public List<String> codigosPermisos() {
        return permisos.stream().map(Permiso::codigo).sorted().toList();
    }
}
