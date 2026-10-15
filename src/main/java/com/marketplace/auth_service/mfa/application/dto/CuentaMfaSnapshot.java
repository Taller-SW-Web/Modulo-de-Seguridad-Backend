package com.marketplace.auth_service.mfa.application.dto;

import java.util.Objects;
import java.util.UUID;

/** Proyección mínima del puerto de cuentas, sin entidad ni datos de contacto. */
public record CuentaMfaSnapshot(UUID usuarioId, boolean disponible,
                                boolean correoVerificado, boolean mfaVoluntario) {
    public CuentaMfaSnapshot { Objects.requireNonNull(usuarioId); }
}
