package com.marketplace.auth_service.mfa.adapter.out.entrega;

import com.marketplace.auth_service.mfa.application.port.out.EntregaOtpPort;
import com.marketplace.auth_service.mfa.domain.exception.EntregaOtpNoDisponibleException;
import com.marketplace.auth_service.mfa.domain.model.CanalOtp;

/** Sin SMTP configurado: la solicitud revierte (503) sin consumir cuota ni simular un envío. */
public final class EntregaOtpNoDisponible implements EntregaOtpPort {
    @Override
    public void programar(CanalOtp canal, String destino, String codigo) {
        throw new EntregaOtpNoDisponibleException();
    }
}
