package com.marketplace.auth_service.proteccion.application.port.in;

import com.marketplace.auth_service.proteccion.application.dto.BloqueoVigente;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Consulta de solo lectura de bloqueos vigentes (RF-15.6), para componer el esquema
 * {@code Usuario} de administración. No toma locks ni muta: corre dentro de la
 * transacción de lectura de quien consulta.
 */
public interface ConsultaBloqueosPort {
    /** Solo las cuentas con un bloqueo vigente aparecen en el mapa. */
    Map<UUID, BloqueoVigente> vigentes(Set<UUID> usuarios);
}
