package com.marketplace.auth_service.mfa.application.port.out;

import java.util.Set;
import java.util.UUID;
import com.marketplace.auth_service.shared.domain.Rol;

/** Roles actuales del dueño de autorización, mediante su puerto exportado. */
public interface RolesMfaPort {
    Set<Rol> leer(UUID usuarioId);
}
