package com.marketplace.auth_service.mfa.application.port.out;

import java.util.Set;
import java.util.UUID;

/** Roles actuales de Sergio convertidos a códigos canónicos por un puente. */
public interface RolesMfaPort {
    Set<String> leer(UUID usuarioId);
}
