package com.marketplace.auth_service.mfa.adapter.in.transaction;

import com.marketplace.auth_service.mfa.application.dto.CanalMfa;
import com.marketplace.auth_service.mfa.application.dto.ResultadoSolicitudOtp;
import com.marketplace.auth_service.mfa.application.dto.ResultadoVerificacionOtp;
import com.marketplace.auth_service.mfa.application.port.in.OtpLoginPort;
import java.util.Objects;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transacciones del OTP de login: solicitar confirma por sí mismo (también los rechazos y
 * la invalidación del código previo); verificar exige la del coordinador del login, que
 * abre la sesión en el mismo commit y conserva los intentos fallidos aunque responda 401.
 */
public class OtpLoginTransaccional implements OtpLoginPort {
    private final OtpLoginPort delegado;

    public OtpLoginTransaccional(OtpLoginPort delegado) {
        this.delegado = Objects.requireNonNull(delegado);
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoSolicitudOtp solicitar(String challengeToken, CanalMfa canal) {
        return delegado.solicitar(challengeToken, canal);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public ResultadoVerificacionOtp verificar(String challengeToken, String codigo) {
        return delegado.verificar(challengeToken, codigo);
    }
}
