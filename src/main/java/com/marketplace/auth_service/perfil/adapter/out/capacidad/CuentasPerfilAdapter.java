package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.perfil.application.dto.CuentaBasica;
import com.marketplace.auth_service.perfil.application.port.out.CuentasPerfilPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.Optional;
import java.util.UUID;

/** Puente a CuentasPort (Eva): toma identidad y estado efectivo; ignora contadores y bandera MFA. */
public final class CuentasPerfilAdapter implements CuentasPerfilPort {
    private final CuentasPort cuentas;

    public CuentasPerfilAdapter(CuentasPort cuentas) {
        this.cuentas = cuentas;
    }

    @Override
    public Optional<CuentaBasica> leer(UUID usuarioId) {
        return cuentas.leer(usuarioId).map(s -> new CuentaBasica(
                s.id(), s.nombres(), s.apellidos(), s.nombreCompleto(), s.correo(), s.celular(),
                s.estadoEfectivo(), s.correoVerificado(), s.celularVerificado()));
    }
}
