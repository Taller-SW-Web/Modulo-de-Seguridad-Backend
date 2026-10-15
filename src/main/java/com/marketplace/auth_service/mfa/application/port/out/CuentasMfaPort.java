package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.mfa.application.dto.ContactoMfa;
import com.marketplace.auth_service.mfa.application.dto.CuentaMfaSnapshot;
import java.util.Optional;
import java.util.UUID;

/** Necesidad del consumidor MFA; el puente productivo consume CuentasPort. */
public interface CuentasMfaPort {
    Optional<CuentaMfaSnapshot> leer(UUID usuarioId);

    /** Lock de la cuenta antes de los del desafío y el código (orden de locks común). */
    void bloquear(UUID usuarioId);

    /** Destino de entrega del código; nunca se registra. */
    Optional<ContactoMfa> contacto(UUID usuarioId);
}
