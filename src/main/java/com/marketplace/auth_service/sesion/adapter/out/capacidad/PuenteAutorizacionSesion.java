package com.marketplace.auth_service.sesion.adapter.out.capacidad;

import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.sesion.application.dto.RolesSesion;
import com.marketplace.auth_service.sesion.application.port.out.AutorizacionSesionPort;

import java.util.Objects;
import java.util.UUID;

/**
 * Puente hacia los roles de Sergio: implementa el puerto de salida propio y
 * delega únicamente al {@code AutorizacionPort} exportado y su DTO. Los
 * permisos se revalidan en servidor en cada login.
 */
public class PuenteAutorizacionSesion implements AutorizacionSesionPort {

    private final AutorizacionPort autorizacion;

    public PuenteAutorizacionSesion(AutorizacionPort autorizacion) {
        this.autorizacion = Objects.requireNonNull(autorizacion, "autorizacion");
    }

    @Override
    public RolesSesion resolverRoles(UUID usuarioId) {
        PermisosResueltos resueltos = autorizacion.resolver(usuarioId);
        return new RolesSesion(resueltos.roles(), resueltos.codigosPermisos());
    }
}
