package com.marketplace.auth_service.mfa.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identidades propias y ajenas como valores; no contiene entidades de otro dueño. */
public record ContextoOtp(UUID usuarioId, UUID desafioId, UUID otpId, String proposito) {
    public static final String PROPOSITO = "LOGIN_MFA";
    public static final String CAMBIO = "CAMBIO_MFA";

    public ContextoOtp(UUID usuarioId, UUID desafioId, UUID otpId) {
        this(usuarioId, desafioId, otpId, PROPOSITO);
    }

    public ContextoOtp {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(desafioId, "desafioId");
        Objects.requireNonNull(otpId, "otpId");
        if (!PROPOSITO.equals(proposito) && !CAMBIO.equals(proposito)) {
            throw new IllegalArgumentException("Propósito OTP no admitido");
        }
    }
}
