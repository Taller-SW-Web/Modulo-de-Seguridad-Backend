package com.marketplace.auth_service.autorizacion.application.port.out;

import com.marketplace.auth_service.autorizacion.application.dto.CuentaRoles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que la gestión de roles necesita de las cuentas. El dueño de la tabla
 * {@code usuario} es Eva (EP-01); esta interfaz se cumplirá con un puente en
 * {@code autorizacion.adapter.out.capacidad} hacia su {@code CuentasPort}
 * cuando exista. Mientras tanto no hay implementación y la gestión de roles
 * responde {@code 503 NO_DISPONIBLE}.
 */
public interface CuentasRolesPort {

    /**
     * Bloquea las filas de esas cuentas en la transacción en curso (nivel 2 del
     * orden de locks), una a una y <b>exactamente en el orden recibido</b>: el
     * coordinador ya las entrega sin repetir y ordenadas por UUID (actor y
     * objetivo, ACUERDOS-INTERNOS §3). Devuelve las que existen, por id.
     * Requiere transacción.
     */
    Map<UUID, CuentaRoles> bloquear(List<UUID> usuarioIds);
}
