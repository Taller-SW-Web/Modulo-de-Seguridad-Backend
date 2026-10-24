package com.marketplace.auth_service.direccion.adapter.out.capacidad;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.direccion.application.port.out.AutorizacionDireccionesPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.Permiso;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.Objects;
import java.util.UUID;

/** Puente a {@link AutorizacionPort}: el titular pasa sin consultar; el resto se revalida contra la base. */
public final class AutorizacionDireccionesAdapter implements AutorizacionDireccionesPort {
    private final AutorizacionPort autorizacion;

    public AutorizacionDireccionesAdapter(AutorizacionPort autorizacion) {
        this.autorizacion = Objects.requireNonNull(autorizacion);
    }

    @Override
    public boolean puedeVer(ActorUsuario actor, UUID usuarioId) {
        return actor.esTitularDe(usuarioId) || autorizacion.resolver(actor.sub()).tiene(Permiso.USUARIO_VER);
    }

    @Override
    public boolean puedeAgregar(ActorUsuario actor, UUID usuarioId) {
        return actor.esTitularDe(usuarioId) || autorizacion.resolver(actor.sub()).tiene(Rol.ADMIN_SISTEMA);
    }
}
