package com.marketplace.auth_service.mfa.application.port.out;

import java.util.UUID;

/** Hecho no crítico {@code OTP_SOLICITADO} (SPEC-09 RF-09.12), sin el código ni el destino. */
public interface HechosMfaPort {
    void otpSolicitado(UUID usuarioId);
}
