package com.marketplace.auth_service.sesion.application.port.out;

import com.marketplace.auth_service.sesion.application.dto.DatosIdentidad;

import java.util.UUID;

/**
 * Identidad y reglas para renovar, resueltas por sus dueños (Eva/Luis/Sergio).
 * Un bloqueo automático previo permite renovar; uno manual o la baja, no.
 */
public interface ProveedorIdentidadPort {
    DatosIdentidad identidadParaRenovar(UUID usuarioId);
}
