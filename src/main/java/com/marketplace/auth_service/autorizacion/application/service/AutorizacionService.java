package com.marketplace.auth_service.autorizacion.application.service;

import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AsociacionesRolPort;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Casos de uso de autorización, en Java puro (sin Spring ni JPA).
 *
 * <p>Los permisos salen de {@code rol_permiso}: la base es la fuente de verdad
 * de lo concedido. Si la base devolviera un código que no está en el catálogo
 * de este módulo, se falla en voz alta en vez de firmarlo: así nunca entra en
 * un token un permiso de otra API (RF-11.9).
 */
public class AutorizacionService implements AutorizacionPort {

    private final AsociacionesRolPort asociaciones;

    public AutorizacionService(AsociacionesRolPort asociaciones) {
        this.asociaciones = Objects.requireNonNull(asociaciones, "asociaciones");
    }

    @Override
    public PermisosResueltos resolver(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Set<Rol> roles = asociaciones.rolesDe(usuarioId);
        if (roles.isEmpty()) {
            return PermisosResueltos.ninguno();
        }
        Set<Permiso> permisos = EnumSet.noneOf(Permiso.class);
        for (String codigo : asociaciones.permisosDe(usuarioId)) {
            permisos.add(Permiso.desdeCodigo(codigo).orElseThrow(() -> new IllegalStateException(
                    "rol_permiso contiene un permiso fuera del catálogo de Seguridad: " + codigo)));
        }
        return new PermisosResueltos(roles, permisos);
    }
}
