package com.marketplace.auth_service.mfa.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identidades propias y ajenas como valores; no contiene entidades de otro dueño. */
public record ContextoOtp(UUID usuarioId, UUID desafioId, UUID otpId) {
    public static final String PROPOSITO = "LOGIN_MFA";

    public ContextoOtp {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(desafioId, "desafioId");
        Objects.requireNonNull(otpId, "otpId");
    }
}
