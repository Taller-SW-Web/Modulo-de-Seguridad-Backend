package com.marketplace.auth_service.autorizacion.application.port.in;

import com.marketplace.auth_service.shared.domain.Rol;
import java.util.Set;
import java.util.UUID;

/**
 * Roles iniciales de una cuenta de prueba (EP-01-T007). Solo existe cuando la
 * carga de cuentas de prueba está habilitada. No pasa por la gestión de roles
 * porque una cuenta recién creada no tiene actor, sesiones que revocar ni evento
 * que publicar. Requiere la transacción del llamador.
 */
public interface RolesPruebaInterna {
    /** Añade los roles que falten; nunca quita los que la cuenta ya tenga. */
    void asegurar(UUID usuarioId, Set<Rol> roles);
}
