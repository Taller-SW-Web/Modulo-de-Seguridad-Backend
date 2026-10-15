package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.perfil.application.port.out.RolesPerfilPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Puente a AutorizacionPort.resolver (Sergio): roles actuales de la base. */
public final class RolesPerfilAdapter implements RolesPerfilPort {
    private final AutorizacionPort autorizacion;

    public RolesPerfilAdapter(AutorizacionPort autorizacion) {
        this.autorizacion = autorizacion;
    }

    @Override
    public Set<String> rolesDe(UUID usuarioId) {
        return autorizacion.resolver(usuarioId).roles().stream().map(Rol::name).collect(Collectors.toUnmodifiableSet());
    }
}
