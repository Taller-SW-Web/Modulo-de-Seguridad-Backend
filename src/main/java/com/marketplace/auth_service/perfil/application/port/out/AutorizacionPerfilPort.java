package com.marketplace.auth_service.perfil.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.UUID;

/** Titular o privilegio usuario.ver, revalidado contra permisos actuales (no contra el token). */
public interface AutorizacionPerfilPort {
    boolean puedeVerPerfil(ActorUsuario actor, UUID objetivoId);
}
