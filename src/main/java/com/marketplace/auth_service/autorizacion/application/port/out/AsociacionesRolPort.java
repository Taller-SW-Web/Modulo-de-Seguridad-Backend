package com.marketplace.auth_service.autorizacion.application.port.out;

import com.marketplace.auth_service.autorizacion.domain.model.Rol;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que la autorización necesita de su persistencia: tablas {@code rol},
 * {@code permiso}, {@code rol_permiso} y {@code usuario_rol}, todas de Sergio
 * (DISENO-PERSISTENCIA §1). Lo implementa el adaptador JPA.
 */
public interface AsociacionesRolPort {

    /** Roles asignados a una persona; vacío si no tiene ninguno o no existe. */
    Set<Rol> rolesDe(UUID usuarioId);

    /**
     * Códigos de permiso que conceden sus roles, sin duplicados, tal como están
     * en {@code rol_permiso}. Una sola consulta indexada.
     */
    Set<String> permisosDe(UUID usuarioId);

    /** Matriz rol → códigos de permiso guardada en la base, para compararla con el catálogo. */
    Map<Rol, Set<String>> matrizPersistida();

    /** Personas que tienen el rol. */
    Set<UUID> usuariosConRol(Rol rol);

    /**
     * {@code SELECT ... FROM rol WHERE codigo = 'ADMIN_SISTEMA' FOR UPDATE} en la
     * transacción en curso.
     *
     * @throws IllegalStateException si la fila no existe (catálogo sin sembrar)
     */
    void bloquearFilaAdministrador();
}
