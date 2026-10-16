package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.mfa.domain.model.CanalOtp;

/**
 * Entrega del código al titular (RF-09.4). EMAIL se encola cifrado en el commit del OTP;
 * el worker transporta fuera de locks y reintenta sin generar OTP ni consumir otra cuota.
 * Una falla al encolar revierte la solicitud. Nunca registra el código en logs.
 */
public interface EntregaOtpPort {
    void programar(CanalOtp canal, String destino, String codigo);
}
