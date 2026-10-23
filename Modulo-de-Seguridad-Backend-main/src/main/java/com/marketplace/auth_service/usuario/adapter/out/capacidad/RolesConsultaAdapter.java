package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.port.out.RolesConsultaPort;
import java.util.Set;
import java.util.UUID;

public final class RolesConsultaAdapter implements RolesConsultaPort {
    private final AutorizacionPort autorizacion;
    public RolesConsultaAdapter(AutorizacionPort autorizacion) {
        this.autorizacion = autorizacion;
    }
    @Override
    public Set<UUID> usuariosConRol(String rol) {
        return Set.copyOf(autorizacion.usuariosConRol(Rol.valueOf(rol)));
    }
}
