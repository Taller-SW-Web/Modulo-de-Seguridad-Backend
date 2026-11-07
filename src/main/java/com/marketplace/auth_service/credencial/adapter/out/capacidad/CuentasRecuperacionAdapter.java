package com.marketplace.auth_service.credencial.adapter.out.capacidad;

import com.marketplace.auth_service.credencial.application.dto.CuentaRecuperacion;
import com.marketplace.auth_service.credencial.application.port.out.CuentasRecuperacionPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;

/** Puente de credenciales al dueño de cuentas (Eva), sin consulta directa a sus tablas. */
public final class CuentasRecuperacionAdapter implements CuentasRecuperacionPort {
    private final CuentasPort cuentas;

    public CuentasRecuperacionAdapter(CuentasPort cuentas) {
        this.cuentas = Objects.requireNonNull(cuentas);
    }

    @Override
    public Optional<CuentaRecuperacion> buscar(String correoNormalizado) {
        return cuentas.buscarPorCorreoNormalizado(correoNormalizado.strip().toLowerCase(Locale.ROOT))
                .map(cuenta -> new CuentaRecuperacion(cuenta.id(), cuenta.correo(),
                        "ACTIVO".equals(cuenta.estadoEfectivo()) || "BLOQUEADO".equals(cuenta.estadoEfectivo())));
    }
}
