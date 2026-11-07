package com.marketplace.auth_service.direccion.adapter.out.capacidad;

import com.marketplace.auth_service.direccion.application.port.out.CuentaDireccionesPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.UUID;

/** Puente a la capacidad usuario; no accede a sus tablas ni repositorios. */
public final class CuentaDireccionesAdapter implements CuentaDireccionesPort {
    private final CuentasPort cuentas;
    public CuentaDireccionesAdapter(CuentasPort cuentas) { this.cuentas = cuentas; }
    @Override public boolean existe(UUID usuarioId) { return cuentas.leer(usuarioId).isPresent(); }
    @Override public void bloquear(UUID usuarioId) { cuentas.bloquear(usuarioId); }
}
