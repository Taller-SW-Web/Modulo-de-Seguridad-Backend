package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;

/**
 * FirmaPort cuando la configuración no permite firmar: deniega toda emisión.
 * Así la aplicación arranca (salud, rutas públicas, pruebas de otros dueños)
 * pero ningún login entrega un acceso firmado con una clave improvisada.
 */
public class FirmaDenegada implements FirmaPort {

    private final String motivo;

    public FirmaDenegada(String motivo) {
        this.motivo = motivo;
    }

    @Override
    public AccesoFirmado firmar(ClaimsAcceso claims) {
        throw new FirmaNoDisponibleException(motivo);
    }
}
