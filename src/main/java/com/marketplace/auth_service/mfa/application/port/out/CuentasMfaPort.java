package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.mfa.application.dto.CuentaMfaSnapshot;
import java.util.Optional;
import java.util.UUID;

/** Necesidad del consumidor MFA; el puente productivo consume CuentasPort. */
public interface CuentasMfaPort {
    Optional<CuentaMfaSnapshot> leer(UUID usuarioId);
}
