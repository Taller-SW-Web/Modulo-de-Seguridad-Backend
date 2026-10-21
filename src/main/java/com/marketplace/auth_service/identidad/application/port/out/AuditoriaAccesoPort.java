package com.marketplace.auth_service.identidad.application.port.out;

import com.marketplace.auth_service.identidad.application.dto.DenegacionAcceso;

/**
 * Registro de accesos denegados en la frontera HTTP (SPEC-17 ESC-17.8,
 * {@code ACCESO_DENEGADO} no crítica de SPEC-12). Su fallo nunca cambia el 403.
 */
public interface AuditoriaAccesoPort {

    void accesoDenegado(DenegacionAcceso denegacion);
}
