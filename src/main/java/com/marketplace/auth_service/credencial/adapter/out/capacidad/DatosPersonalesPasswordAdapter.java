package com.marketplace.auth_service.credencial.adapter.out.capacidad;

import com.marketplace.auth_service.credencial.application.dto.DatosPersonalesPassword;
import com.marketplace.auth_service.credencial.application.port.out.DatosPersonalesPasswordPort;
import com.marketplace.auth_service.credencial.domain.exception.PoliticaNoDisponibleException;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;

public final class DatosPersonalesPasswordAdapter implements DatosPersonalesPasswordPort {
    private final CuentasPort cuentas;
    public DatosPersonalesPasswordAdapter(CuentasPort cuentas) { this.cuentas = cuentas; }
    @Override public DatosPersonalesPassword leer(java.util.UUID usuarioId) {
        var cuenta = cuentas.leer(usuarioId).orElseThrow(PoliticaNoDisponibleException::new);
        return new DatosPersonalesPassword(cuenta.nombres(), cuenta.apellidos(), cuenta.correo());
    }
}
