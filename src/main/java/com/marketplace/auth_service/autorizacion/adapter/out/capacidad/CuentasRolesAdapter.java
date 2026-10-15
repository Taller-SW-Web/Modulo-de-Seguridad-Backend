package com.marketplace.auth_service.autorizacion.adapter.out.capacidad;

import com.marketplace.auth_service.autorizacion.application.dto.CuentaRoles;
import com.marketplace.auth_service.autorizacion.application.port.out.CuentasRolesPort;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puente a {@code CuentasPort} de cuentas (Eva, EP-01-T002). Bloquea cada fila
 * con su {@code bloquear} ({@code MANDATORY}: participa en la transacción del
 * cambio de rol) en el orden exacto recibido, y entrega el estado <b>efectivo</b>.
 */
public final class CuentasRolesAdapter implements CuentasRolesPort {

    private final CuentasPort cuentas;

    public CuentasRolesAdapter(CuentasPort cuentas) {
        this.cuentas = Objects.requireNonNull(cuentas, "cuentas");
    }

    @Override
    public Map<UUID, CuentaRoles> bloquear(List<UUID> usuarioIds) {
        Map<UUID, CuentaRoles> bloqueadas = new LinkedHashMap<>();
        for (UUID id : usuarioIds) {
            // Una cuenta inexistente no se bloquea: el coordinador responde 404 por su ausencia.
            Optional<CuentaSnapshot> existente = cuentas.leer(id);
            if (existente.isPresent()) {
                bloqueadas.put(id, aCuentaRoles(cuentas.bloquear(id)));
            }
        }
        return bloqueadas;
    }

    @Override
    public long contarActivos(Set<UUID> usuarioIds) {
        return usuarioIds.isEmpty() ? 0 : cuentas.contarActivos(usuarioIds);
    }

    private static CuentaRoles aCuentaRoles(CuentaSnapshot cuenta) {
        return new CuentaRoles(cuenta.id(), cuenta.nombreCompleto(), cuenta.correo(), cuenta.estadoEfectivo());
    }
}
