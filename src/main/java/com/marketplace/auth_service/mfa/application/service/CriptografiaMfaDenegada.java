package com.marketplace.auth_service.mfa.application.service;

import com.marketplace.auth_service.mfa.application.dto.DesafioGenerado;
import com.marketplace.auth_service.mfa.application.dto.OtpGenerado;
import com.marketplace.auth_service.mfa.application.port.out.CriptografiaMfaPort;
import com.marketplace.auth_service.mfa.domain.exception.CriptografiaMfaNoDisponibleException;
import com.marketplace.auth_service.mfa.domain.model.ContextoOtp;

/** Permite arrancar sin secretos de otro dueño, pero nunca generar un desafío/OTP utilizable. */
public final class CriptografiaMfaDenegada implements CriptografiaMfaPort {
    @Override public DesafioGenerado generarDesafio() { throw new CriptografiaMfaNoDisponibleException(); }
    @Override public String hashDesafio(String token) { throw new CriptografiaMfaNoDisponibleException(); }
    @Override public OtpGenerado generarOtp(ContextoOtp contexto) { throw new CriptografiaMfaNoDisponibleException(); }
    @Override public boolean coincideOtp(ContextoOtp contexto, String codigo, String hashCodigo, String hashKeyId) {
        throw new CriptografiaMfaNoDisponibleException();
    }
}
