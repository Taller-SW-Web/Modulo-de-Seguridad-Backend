package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.mfa.domain.model.CanalOtp;

/**
 * Entrega del código al titular (RF-09.4). El adaptador lo envía después del commit, fuera
 * de locks, y nunca lo escribe en logs. Si el transporte no está configurado lanza una
 * excepción antes del commit: no se consume cuota sin poder enviar.
 */
public interface EntregaOtpPort {
    void programar(CanalOtp canal, String destino, String codigo);
}
