package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.perfil.application.port.out.AutorizacionPerfilPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.Permiso;
import java.util.UUID;

/** Titular, o usuario.ver revalidado con los permisos actuales de la base (no los del token). */
public final class AutorizacionPerfilAdapter implements AutorizacionPerfilPort {
    private final AutorizacionPort autorizacion;

    public AutorizacionPerfilAdapter(AutorizacionPort autorizacion) {
        this.autorizacion = autorizacion;
    }

    @Override
    public boolean puedeVerPerfil(ActorUsuario actor, UUID objetivoId) {
        return actor.esTitularDe(objetivoId) || autorizacion.resolver(actor.sub()).tiene(Permiso.USUARIO_VER);
    }
}
