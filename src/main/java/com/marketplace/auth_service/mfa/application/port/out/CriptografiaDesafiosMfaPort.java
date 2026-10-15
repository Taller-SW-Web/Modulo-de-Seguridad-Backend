package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.mfa.application.dto.DesafioGenerado;

/** Solo necesidades de desafío; no obliga a T002 a depender de operaciones OTP. */
public interface CriptografiaDesafiosMfaPort {
    DesafioGenerado generarDesafio();
    String hashDesafio(String token);
}
