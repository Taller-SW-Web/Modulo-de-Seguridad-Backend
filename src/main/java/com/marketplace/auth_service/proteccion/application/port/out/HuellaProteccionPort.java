package com.marketplace.auth_service.proteccion.application.port.out;

import com.marketplace.auth_service.proteccion.domain.model.HuellaFallo;
import com.marketplace.auth_service.proteccion.domain.model.RachaFallo;
import java.time.Instant;

/** Puerto de salida interno. T002 exportará ProteccionPort y sus DTO. */
public interface HuellaProteccionPort {
    String claveActualId();
    HuellaFallo generar(RachaFallo racha, byte[] secretoEfimero, Instant ahora);
}
