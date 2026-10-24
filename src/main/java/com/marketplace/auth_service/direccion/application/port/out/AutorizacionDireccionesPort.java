package com.marketplace.auth_service.direccion.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.UUID;

/** Quién puede leer o añadir direcciones, decidido con los permisos actuales y no con los del token. */
public interface AutorizacionDireccionesPort {

    /** Titular de la cuenta, o quien tenga hoy el permiso {@code usuario.ver}. */
    boolean puedeVer(ActorUsuario actor, UUID usuarioId);

    /** Titular de la cuenta, o quien tenga hoy el rol {@code ADMIN_SISTEMA}. */
    boolean puedeAgregar(ActorUsuario actor, UUID usuarioId);
}
