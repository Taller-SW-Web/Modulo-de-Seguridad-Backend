package com.marketplace.auth_service.identidad.adapter.out.capacidad;

import com.marketplace.auth_service.identidad.application.dto.CuentaIdentidad;
import com.marketplace.auth_service.identidad.application.port.out.CuentasIdentidadPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.Optional;
import java.util.UUID;

/** Puente a CuentasPort (Eva): identidad, contacto y estado efectivo, sin documento ni contadores. */
public final class CuentasIdentidadAdapter implements CuentasIdentidadPort {

    private final CuentasPort cuentas;

    public CuentasIdentidadAdapter(CuentasPort cuentas) {
        this.cuentas = cuentas;
    }

    @Override
    public Optional<CuentaIdentidad> leer(UUID usuarioId) {
        return cuentas.leer(usuarioId).map(s -> new CuentaIdentidad(s.id(), s.nombreCompleto(), s.correo(),
                s.celular(), s.estadoEfectivo()));
    }
}
