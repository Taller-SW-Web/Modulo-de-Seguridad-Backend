package com.marketplace.auth_service.autorizacion.adapter.out.persistence.mapper;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.RolPermisoJpaEntity;
import com.marketplace.auth_service.shared.domain.Rol;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Entidades JPA → modelo de autorización. Manual y explícito (BACKEND-HEXAGONAL §3). */
public final class AutorizacionPersistenceMapper {

    private AutorizacionPersistenceMapper() {
    }

    public static Set<Rol> aRoles(Collection<Rol> filas) {
        return filas.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(filas));
    }

    public static Set<String> aCodigos(Collection<String> filas) {
        return Set.copyOf(new TreeSet<>(filas));
    }

    /** Agrupa {@code rol_permiso} por rol; los roles sin filas quedan con un conjunto vacío. */
    public static Map<Rol, Set<String>> aMatriz(Collection<RolPermisoJpaEntity> filas) {
        Map<Rol, Set<String>> matriz = new EnumMap<>(Rol.class);
        for (Rol rol : Rol.values()) {
            matriz.put(rol, new TreeSet<>());
        }
        filas.forEach(fila -> matriz.get(fila.getRol()).add(fila.getPermisoCodigo()));
        matriz.replaceAll((rol, permisos) -> Set.copyOf(permisos));
        return Map.copyOf(matriz);
    }
}
