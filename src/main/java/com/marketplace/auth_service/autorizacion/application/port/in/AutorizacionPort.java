package com.marketplace.auth_service.autorizacion.application.port.in;

import com.marketplace.auth_service.autorizacion.application.dto.PermisosResueltos;

import java.util.UUID;

/**
 * Capacidad exportada de autorización (ACUERDOS-INTERNOS §2). Los demás
 * dueños la consumen solo por este puerto, nunca por los repositorios.
 *
 * <p>Solo expone tipos exportables: DTO de {@code application.dto} y
 * vocabulario común de {@code shared.domain}. Un consumidor lo usa desde un
 * puente en su {@code adapter.out.capacidad}.
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
    PermisosResueltos resolver(UUID usuarioId);
}
