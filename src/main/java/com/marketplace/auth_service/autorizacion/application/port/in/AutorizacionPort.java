package com.marketplace.auth_service.autorizacion.application.port.in;

import com.marketplace.auth_service.autorizacion.domain.model.PermisosEfectivos;

import java.util.UUID;

/**
 * Capacidad exportada de autorización (ACUERDOS-INTERNOS §2). Los demás
 * dueños la consumen solo por este puerto, nunca por los repositorios.
 *
 * <p>EP-05-T001 entrega {@code resolver}. EP-05-T002 añade
 * {@code exigirPermiso}, {@code exigirTitularOPrivilegio},
 * {@code bloquearCatalogoAdministrador} y {@code usuariosConRol}.
 */
public interface AutorizacionPort {

    /**
     * Roles y permisos actuales de una persona, leídos de la base en cada
     * llamada (RF-11.4). El login los pone en el acceso; un cambio de roles se
     * refleja en la siguiente emisión.
     *
     * @return arrays vacíos si la persona no tiene roles; nunca null
     */
    PermisosEfectivos resolver(UUID usuarioId);
}
