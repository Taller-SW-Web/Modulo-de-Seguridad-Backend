package com.marketplace.auth_service.identidad.adapter.out.capacidad;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.identidad.application.port.out.RolesIdentidadPort;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.List;
import java.util.UUID;

/** Puente a AutorizacionPort.resolver (Sergio): roles actuales de la base. */
public final class RolesIdentidadAdapter implements RolesIdentidadPort {

    private final AutorizacionPort autorizacion;

    public RolesIdentidadAdapter(AutorizacionPort autorizacion) {
        this.autorizacion = autorizacion;
    }

    @Override
    public List<String> rolesDe(UUID usuarioId) {
        return autorizacion.resolver(usuarioId).roles().stream().map(Rol::name).toList();
    }
}
