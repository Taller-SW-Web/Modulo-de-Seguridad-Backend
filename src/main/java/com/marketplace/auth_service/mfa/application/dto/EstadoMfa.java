package com.marketplace.auth_service.mfa.application.dto;

/** mfaHabilitado es el valor efectivo, no una copia ciega de la bandera voluntaria. */
public record EstadoMfa(boolean mfaHabilitado, boolean obligatorio) {
    public EstadoMfa {
        if (obligatorio && !mfaHabilitado) throw new IllegalArgumentException("MFA obligatorio debe estar habilitado");
    }
}
