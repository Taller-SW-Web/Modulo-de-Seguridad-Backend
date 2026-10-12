package com.marketplace.auth_service.autorizacion.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Matriz cerrada rol → permisos de este módulo (RF-11.9): los ocho de
 * administración son de {@code ADMIN_SISTEMA}; {@code cliente.registrar} y
 * {@code cliente.buscar}, de {@code VENDEDOR}; el resto de roles no tiene
 * permisos de Seguridad.
 *
 * <p>Es la referencia con la que se siembra {@code rol_permiso}
 * ({@code db/autorizacion/R__catalogo_autorizacion.sql}) y con la que una
 * prueba comprueba que la base no se ha desviado.
 */
public final class CatalogoAutorizacion {

    private static final Map<Rol, Set<Permiso>> MATRIZ;

    static {
        Map<Rol, Set<Permiso>> matriz = new EnumMap<>(Rol.class);
        for (Rol rol : Rol.values()) {
            matriz.put(rol, Collections.unmodifiableSet(EnumSet.noneOf(Permiso.class)));
        }
        matriz.put(Rol.ADMIN_SISTEMA, Collections.unmodifiableSet(EnumSet.of(
                Permiso.USUARIO_VER, Permiso.USUARIO_CREAR, Permiso.USUARIO_DESACTIVAR, Permiso.USUARIO_REACTIVAR,
                Permiso.ROL_ASIGNAR, Permiso.AUDITORIA_VER, Permiso.CUENTA_BLOQUEAR, Permiso.ATRIBUTOS_EDITAR)));
        matriz.put(Rol.VENDEDOR, Collections.unmodifiableSet(EnumSet.of(
                Permiso.CLIENTE_REGISTRAR, Permiso.CLIENTE_BUSCAR)));
        MATRIZ = Collections.unmodifiableMap(matriz);
    }

    private CatalogoAutorizacion() {
    }

    /** Permisos que concede un rol; vacío si no concede ninguno de este módulo. */
    public static Set<Permiso> permisosDe(Rol rol) {
        return MATRIZ.get(rol);
    }

    /** La matriz completa, para sembrar o comparar con la base. */
    public static Map<Rol, Set<Permiso>> matriz() {
        return MATRIZ;
    }
}
