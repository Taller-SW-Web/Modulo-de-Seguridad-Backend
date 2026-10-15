package com.marketplace.auth_service.sesion.application.port.out;

import com.marketplace.auth_service.sesion.application.dto.RolesSesion;

import java.util.UUID;

/**
 * Roles y permisos vigentes para el login (Sergio, SPEC-11). Se revalidan en
 * servidor en cada login: lo firmado antes no decide.
 */
public interface AutorizacionSesionPort {
    RolesSesion resolverRoles(UUID usuarioId);
}
