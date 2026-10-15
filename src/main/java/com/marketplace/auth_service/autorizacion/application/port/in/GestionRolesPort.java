package com.marketplace.auth_service.autorizacion.application.port.in;

import com.marketplace.auth_service.autorizacion.application.dto.CambioRolCommand;
import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiados;

/**
 * Asignar y revocar roles, de uno en uno (SPEC-11, RF-11.3). Lo usa el
 * controlador HTTP de roles; ninguna otra capacidad cambia roles.
 *
 * <p>Cada llamada es una sola transacción: asociación, revocación de las
 * sesiones del objetivo, auditoría crítica y evento en el outbox se confirman
 * juntos o no se confirma nada (ACUERDOS-INTERNOS §4, «Cambio rol»).
 */
public interface GestionRolesPort {

    /**
     * Asigna un rol (POST {@code /usuarios/{id}/roles}).
     *
     * @throws com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException
     *         sin {@code rol.asignar} actual
     * @throws com.marketplace.auth_service.autorizacion.domain.exception.RolInvalidoException
     *         código fuera del catálogo
     * @throws com.marketplace.auth_service.autorizacion.domain.exception.UsuarioNoEncontradoException
     *         la cuenta objetivo no existe
     * @throws com.marketplace.auth_service.autorizacion.domain.exception.GestionRolesNoDisponibleException
     *         falta un proveedor o no se pudo guardar la auditoría crítica o el outbox
     */
    RolesCambiados asignar(CambioRolCommand comando);
}
