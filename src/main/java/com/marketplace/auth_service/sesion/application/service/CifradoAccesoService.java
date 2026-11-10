package com.marketplace.auth_service.sesion.application.service;

import com.marketplace.auth_service.sesion.application.dto.SesionIniciada;
import com.marketplace.auth_service.sesion.application.port.in.CifradoAccesoPort;
import com.marketplace.auth_service.sesion.application.port.out.FirmadorAccesoPort;

import java.util.Objects;

/**
 * Envuelve el acceso ya emitido (FEAT-16). No abre ni renueva sesiones: la
 * familia, el {@code jti} y la vigencia son los del acceso firmado.
 */
public class CifradoAccesoService implements CifradoAccesoPort {

    private final FirmadorAccesoPort firmador;

    public CifradoAccesoService(FirmadorAccesoPort firmador) {
        this.firmador = Objects.requireNonNull(firmador, "firmador");
    }

    @Override
    public SesionIniciada cifrar(SesionIniciada sesion) {
        Objects.requireNonNull(sesion, "sesion");
        return new SesionIniciada(firmador.cifrar(sesion.accessToken()), sesion.refreshToken(),
                sesion.expiresIn(), sesion.expiraAccesoEn(), sesion.expiraRefrescoEn(), sesion.usuario());
    }
}
