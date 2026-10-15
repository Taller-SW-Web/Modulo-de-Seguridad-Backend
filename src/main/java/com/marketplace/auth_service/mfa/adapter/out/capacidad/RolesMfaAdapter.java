package com.marketplace.auth_service.mfa.adapter.out.capacidad;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.mfa.application.port.out.RolesMfaPort;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.Set;
import java.util.UUID;

public final class RolesMfaAdapter implements RolesMfaPort {
    private final AutorizacionPort autorizacion;
    public RolesMfaAdapter(AutorizacionPort autorizacion) { this.autorizacion = autorizacion; }
    @Override public Set<Rol> leer(UUID usuarioId) { return autorizacion.resolver(usuarioId).roles(); }
}
