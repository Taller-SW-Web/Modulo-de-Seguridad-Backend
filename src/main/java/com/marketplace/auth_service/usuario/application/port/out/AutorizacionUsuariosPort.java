package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.usuario.application.dto.RolesDeCuenta;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Necesidades de la consulta de cuentas ante autorización (Sergio). El puente
 * delega en {@code AutorizacionPort}; aquí no se lee {@code usuario_rol}.
 */
public interface AutorizacionUsuariosPort {

    /** Exige {@code usuario.ver} con los permisos actuales; si falta, lanza la denegación de autorización. */
    void exigirVerUsuarios(ActorUsuario actor);

    /** Roles de cada UUID en una consulta; quien no tiene roles recibe {@link RolesDeCuenta#NINGUNO}. */
    Map<UUID, RolesDeCuenta> rolesDe(Set<UUID> usuarioIds);
}
