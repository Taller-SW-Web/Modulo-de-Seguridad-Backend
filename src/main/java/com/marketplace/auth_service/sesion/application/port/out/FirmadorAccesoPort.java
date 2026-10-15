package com.marketplace.auth_service.sesion.application.port.out;

import com.marketplace.auth_service.sesion.application.dto.DatosAcceso;
import com.marketplace.auth_service.sesion.application.dto.TokenAccesoFirmado;

/**
 * Firma del acceso de persona (Sergio, SPEC-11/RF-05.6). El puente en
 * {@code adapter.out.capacidad} delega al {@code FirmaPort} exportado; el
 * núcleo no importa puertos ni DTO de otra capacidad.
 */
public interface FirmadorAccesoPort {
    TokenAccesoFirmado firmar(DatosAcceso datos);
}
