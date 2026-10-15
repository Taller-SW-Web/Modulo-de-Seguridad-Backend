package com.marketplace.auth_service.proteccion.application.service;

import com.marketplace.auth_service.proteccion.application.port.out.HuellaProteccionPort;
import com.marketplace.auth_service.proteccion.domain.exception.HuellaNoDisponibleException;
import com.marketplace.auth_service.proteccion.domain.model.HuellaFallo;
import com.marketplace.auth_service.proteccion.domain.model.RachaFallo;
import java.time.Instant;

public final class HuellaProteccionDenegada implements HuellaProteccionPort {
    @Override public String claveActualId() { throw new HuellaNoDisponibleException(); }
    @Override public HuellaFallo generar(RachaFallo racha, byte[] secreto, Instant ahora) {
        throw new HuellaNoDisponibleException();
    }
}
