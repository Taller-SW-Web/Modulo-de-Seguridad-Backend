package com.marketplace.auth_service.autorizacion.application.dto;

import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Roles y permisos actuales de una persona, tal como los exporta
 * {@code AutorizacionPort.resolver} a otras capacidades (RF-11.4). Es un DTO
 * inmutable que solo usa tipos comunes ({@link Rol}, {@link Permiso}); el
 * modelo interno {@code PermisosEfectivos} no sale de autorización.
 *
 * <p>Las listas de códigos salen en orden alfabético, así dos emisiones con los
 * mismos roles producen exactamente el mismo token (ESC-11.4:
 * {@code ["ADMIN_SISTEMA", "VENDEDOR"]}).
 *
 * @param roles    roles asignados; vacío si no tiene ninguno, nunca null
 * @param permisos unión sin duplicados de sus permisos; vacío si ninguno, nunca null
 */
public record PermisosResueltos(Set<Rol> roles, Set<Permiso> permisos) {

    public PermisosResueltos {
        roles = Collections.unmodifiableSet(roles.isEmpty() ? EnumSet.noneOf(Rol.class) : EnumSet.copyOf(roles));
        permisos = Collections.unmodifiableSet(
                permisos.isEmpty() ? EnumSet.noneOf(Permiso.class) : EnumSet.copyOf(permisos));
    }

    /** Persona sin roles: arrays vacíos, nunca null. */
    public static PermisosResueltos ninguno() {
        return new PermisosResueltos(Set.of(), Set.of());
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
