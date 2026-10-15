package com.marketplace.auth_service.perfil.application.port.out;

import java.util.Set;
import java.util.UUID;

/** Necesidad de perfil hacia autorización (Sergio): códigos de rol vigentes. Vacío si no tiene. */
public interface RolesPerfilPort {
    Set<String> rolesDe(UUID usuarioId);
}
