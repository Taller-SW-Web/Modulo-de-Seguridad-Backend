package com.marketplace.auth_service.autorizacion.application.port.out;

import java.util.UUID;

/**
 * Revocación de sesiones al cambiar roles (RF-11.5). Se cumplirá con un puente
 * hacia {@code SesionesPort.revocarTodas} de Jose Luis (EP-02), que bloquea
 * las raíces de familia en orden (nivel 3) dentro de la misma transacción.
 */
public interface SesionesRolesPort {

    /** Revoca todas las familias de refresco de la persona. Requiere transacción. */
    void revocarTodas(UUID usuarioId);
}
