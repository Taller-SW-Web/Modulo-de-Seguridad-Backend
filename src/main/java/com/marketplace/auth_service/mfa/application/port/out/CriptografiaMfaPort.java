package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.mfa.application.dto.OtpGenerado;
import com.marketplace.auth_service.mfa.domain.model.ContextoOtp;

/** Necesidad de criptografía del núcleo MFA; distinta del puerto de entrada MfaPort de T002. */
public interface CriptografiaMfaPort extends CriptografiaDesafiosMfaPort {
    OtpGenerado generarOtp(ContextoOtp contexto);
    boolean coincideOtp(ContextoOtp contexto, String codigo, String hashCodigo, String hashKeyId);
}
