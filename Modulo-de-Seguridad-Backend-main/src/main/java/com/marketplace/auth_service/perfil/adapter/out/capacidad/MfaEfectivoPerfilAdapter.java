package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import com.marketplace.auth_service.perfil.application.port.out.MfaEfectivoPerfilPort;
import com.marketplace.auth_service.perfil.domain.model.CuentaPerfilNoEncontradaException;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.UUID;

/**
 * MFA efectivo = bandera voluntaria (Eva) o cualquier rol de gestión (Sergio), igual que el
 * ejemplo de consumo de autorización. TEMPORAL: reemplazar por MfaPort.esEfectivo cuando Luis lo entregue.
 */
public final class MfaEfectivoPerfilAdapter implements MfaEfectivoPerfilPort {
    private final CuentasPort cuentas;
    private final AutorizacionPort autorizacion;

    public MfaEfectivoPerfilAdapter(CuentasPort cuentas, AutorizacionPort autorizacion) {
        this.cuentas = cuentas;
        this.autorizacion = autorizacion;
    }

    @Override
    public boolean efectivo(UUID usuarioId) {
        boolean voluntaria = cuentas.leer(usuarioId)
                .orElseThrow(() -> new CuentaPerfilNoEncontradaException(usuarioId))
                .mfaHabilitado();
        return voluntaria || autorizacion.resolver(usuarioId).roles().stream().anyMatch(Rol::esDeGestion);
    }
}
