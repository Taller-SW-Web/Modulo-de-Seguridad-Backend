package com.marketplace.auth_service.identidad.application.port.out;

import com.marketplace.auth_service.identidad.application.dto.CuentaIdentidad;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Puente hacia la cuenta básica (SPEC-18): el dueño físico de la tabla es {@code usuario}. */
public interface CuentasIdentidadPort {
    Optional<CuentaIdentidad> leer(UUID usuarioId);

    /**
     * Lote (SPEC-18 RF-18.2, FEAT-06-T2): solo las encontradas, sin orden garantizado.
     * Implementación por defecto para los dobles de prueba existentes; el adaptador real
     * la sobrescribe con {@code CuentasPort.leerLote} (dos consultas, sin N+1).
     */
    default Map<UUID, CuentaIdentidad> leerLote(Set<UUID> usuarioIds) {
        Map<UUID, CuentaIdentidad> encontradas = new LinkedHashMap<>();
        for (UUID id : usuarioIds) {
            leer(id).ifPresent(cuenta -> encontradas.put(id, cuenta));
        }
        return encontradas;
    }
}
