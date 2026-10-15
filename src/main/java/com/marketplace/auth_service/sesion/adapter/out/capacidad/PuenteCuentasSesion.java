package com.marketplace.auth_service.sesion.adapter.out.capacidad;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.sesion.application.dto.DatosIdentidad;
import com.marketplace.auth_service.sesion.application.port.out.BloqueoCuentaSesionPort;
import com.marketplace.auth_service.sesion.application.port.out.ProveedorIdentidadPort;
import com.marketplace.auth_service.sesion.domain.exception.DependenciaNoDisponibleException;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.util.List;
import java.util.UUID;

/** Cuentas/roles reales. No decide el tipo de bloqueo que pertenece a Luis. */
public final class PuenteCuentasSesion implements BloqueoCuentaSesionPort, ProveedorIdentidadPort {
    private final CuentasPort cuentas;
    private final AutorizacionPort autorizacion;
    public PuenteCuentasSesion(CuentasPort cuentas, AutorizacionPort autorizacion) {
        this.cuentas = cuentas;
        this.autorizacion = autorizacion;
    }
    @Override public void bloquear(UUID usuarioId) { cuentas.bloquear(usuarioId); }
    @Override public DatosIdentidad identidadParaRenovar(UUID usuarioId) {
        var cuenta = cuentas.leer(usuarioId).orElseThrow(() ->
                new DependenciaNoDisponibleException("No se pudo revalidar la cuenta"));
        if (!"ACTIVO".equals(cuenta.estadoBase())) {
            return new DatosIdentidad(usuarioId, cuenta.correo(), cuenta.nombreCompleto(), List.of(), List.of(), false);
        }
        if ("BLOQUEADO".equals(cuenta.estadoEfectivo())) {
            // Snapshot no distingue manual/automático. No inferirlo ni leer bloqueo.
            throw new DependenciaNoDisponibleException("ProteccionPort: clasificación de bloqueo pendiente");
        }
        if (!"ACTIVO".equals(cuenta.estadoEfectivo())) {
            return new DatosIdentidad(usuarioId, cuenta.correo(), cuenta.nombreCompleto(), List.of(), List.of(), false);
        }
        var permisos = autorizacion.resolver(usuarioId);
        return new DatosIdentidad(usuarioId, cuenta.correo(), cuenta.nombreCompleto(),
                permisos.codigosRoles(), permisos.codigosPermisos(), true);
    }
}
