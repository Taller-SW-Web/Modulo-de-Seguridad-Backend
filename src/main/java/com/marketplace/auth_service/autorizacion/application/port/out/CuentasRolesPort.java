package com.marketplace.auth_service.autorizacion.application.port.out;

import com.marketplace.auth_service.autorizacion.application.dto.CuentaRoles;

import java.util.Optional;
import java.util.Set;
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
     * Bloquea la fila de la cuenta en la transacción en curso (nivel 2 del orden
     * de locks) y devuelve su copia, o vacío si no existe. Requiere transacción.
     */
    Optional<CuentaRoles> bloquear(UUID usuarioId);

    /**
     * Cuántas de esas cuentas están en estado efectivo {@code ACTIVO}
     * ({@code CuentasPort.contarActivos} de Eva). Los UUID los aporta Sergio
     * ({@code usuario_rol}); el estado, Eva. Se llama con el lock del catálogo
     * de administrador tomado, que serializa también bloqueos y bajas.
     */
    long contarActivos(Set<UUID> usuarioIds);
}
