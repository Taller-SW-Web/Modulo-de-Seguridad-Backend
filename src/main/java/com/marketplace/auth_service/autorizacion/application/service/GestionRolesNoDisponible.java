package com.marketplace.auth_service.autorizacion.application.service;

import com.marketplace.auth_service.autorizacion.application.dto.CambioRolCommand;
import com.marketplace.auth_service.autorizacion.application.dto.RolesCambiados;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.in.GestionRolesPort;
import com.marketplace.auth_service.autorizacion.domain.exception.GestionRolesNoDisponibleException;
import com.marketplace.auth_service.autorizacion.domain.model.Permiso;

import java.util.List;
import java.util.Objects;

/**
 * {@link GestionRolesPort} mientras falta algún proveedor (cuentas, sesiones,
 * auditoría u outbox). Un cambio de rol sin esos efectos dejaría sesiones
 * vivas o un cambio sin auditar, así que no se hace ninguno: responde
 * {@code 503 NO_DISPONIBLE}.
 *
 * <p>El permiso se comprueba igual primero, para que alguien sin
 * {@code rol.asignar} reciba 403 y no se entere de qué falta en el servidor.
 */
public class GestionRolesNoDisponible implements GestionRolesPort {

    private final AutorizacionPort autorizacion;
    private final List<String> faltantes;

    public GestionRolesNoDisponible(AutorizacionPort autorizacion, List<String> faltantes) {
        this.autorizacion = Objects.requireNonNull(autorizacion, "autorizacion");
        this.faltantes = List.copyOf(faltantes);
    }

    @Override
    public RolesCambiados asignar(CambioRolCommand comando) {
        autorizacion.exigirPermiso(comando.actor(), Permiso.ROL_ASIGNAR);
        throw new GestionRolesNoDisponibleException("Gestión de roles sin componer; faltan: " + faltantes);
    }

    @Override
    public RolesCambiados revocar(CambioRolCommand comando) {
        return asignar(comando);
    }

    public List<String> faltantes() {
        return faltantes;
    }
}
