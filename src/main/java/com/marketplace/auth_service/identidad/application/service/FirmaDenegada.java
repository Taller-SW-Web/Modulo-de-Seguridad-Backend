package com.marketplace.auth_service.identidad.application.service;

import com.marketplace.auth_service.identidad.application.dto.AccesoFirmado;
import com.marketplace.auth_service.identidad.application.dto.AccesoValidado;
import com.marketplace.auth_service.identidad.application.dto.ClaimsAcceso;
import com.marketplace.auth_service.identidad.application.dto.ClaimsServicio;
import com.marketplace.auth_service.identidad.application.dto.ClavePublica;
import com.marketplace.auth_service.identidad.application.dto.Descubrimiento;
import com.marketplace.auth_service.identidad.application.dto.InspeccionAcceso;
import com.marketplace.auth_service.identidad.application.dto.ServicioValidado;
import com.marketplace.auth_service.identidad.application.dto.TokenServicioFirmado;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;

import java.util.List;

/**
 * FirmaPort cuando la configuración no permite firmar: deniega toda emisión.
 * Así la aplicación arranca (salud, rutas públicas, pruebas de otros dueños)
 * pero ningún login entrega un acceso firmado con una clave improvisada, y el
 * JWKS/descubrimiento responden 503 en lugar de publicar algo incompleto. Sin
 * clave tampoco se puede validar: las rutas protegidas responden 503.
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

    @Override
    public TokenServicioFirmado firmarServicio(ClaimsServicio claims) {
        throw new FirmaNoDisponibleException(motivo);
    }

    @Override
    public List<ClavePublica> jwks() {
        throw new FirmaNoDisponibleException(motivo);
    }

    @Override
    public Descubrimiento descubrimiento() {
        throw new FirmaNoDisponibleException(motivo);
    }

    @Override
    public AccesoValidado validar(String token) {
        throw new FirmaNoDisponibleException(motivo);
    }

    @Override
    public ServicioValidado validarServicio(String token) {
        throw new FirmaNoDisponibleException(motivo);
    }

    @Override
    public InspeccionAcceso inspeccionar(String token) {
        throw new FirmaNoDisponibleException(motivo);
    }
}
