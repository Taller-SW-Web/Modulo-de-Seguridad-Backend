package com.marketplace.auth_service.mfa.application.port.in;

import com.marketplace.auth_service.mfa.application.dto.CanalMfa;
import com.marketplace.auth_service.mfa.application.dto.ResultadoSolicitudOtp;
import com.marketplace.auth_service.mfa.application.dto.ResultadoVerificacionOtp;

/**
 * Código de un solo uso del desafío de login (EP-04-T003/T004, SPEC-09). Solo DTO y tipos
 * Java. {@code solicitar} abre su propia transacción; {@code verificar} participa en la del
 * coordinador del login, que abre la sesión en el mismo commit.
 */
public interface OtpLoginPort {

    ResultadoSolicitudOtp solicitar(String challengeToken, CanalMfa canal);

    ResultadoVerificacionOtp verificar(String challengeToken, String codigo);
}
