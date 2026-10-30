package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.perfil.application.port.out.CuentaCorreoPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Puente a la capacidad usuario; no accede a sus tablas ni repositorios. */
public final class CuentaCorreoAdapter implements CuentaCorreoPort {

    private final CuentasPort cuentas;

    public CuentaCorreoAdapter(CuentasPort cuentas) {
        this.cuentas = Objects.requireNonNull(cuentas);
    }

    @Override
    public Optional<CuentaCorreo> leer(UUID usuarioId) {
        return cuentas.leer(usuarioId).map(c -> new CuentaCorreo(c.id(), c.correo()));
    }

    @Override
    public void bloquear(UUID usuarioId) {
        cuentas.bloquear(usuarioId);
    }

    @Override
    public boolean correoEnUso(String correoNormalizado) {
        return cuentas.buscarPorCorreoNormalizado(correoNormalizado.strip().toLowerCase(Locale.ROOT)).isPresent();
    }
}
