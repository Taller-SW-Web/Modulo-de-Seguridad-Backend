package com.marketplace.auth_service.sesion.adapter.out.capacidad;

import com.marketplace.auth_service.sesion.application.dto.DatosIdentidad;
import com.marketplace.auth_service.sesion.application.port.out.ProveedorIdentidadPort;
import com.marketplace.auth_service.sesion.domain.exception.DependenciaNoDisponibleException;

import java.util.UUID;

/**
 * Marcador hasta que Eva entregue {@code CuentasPort} (cuenta/estado) y Luis
 * su protección: renovar exige sus reglas y sesiones no consulta sus tablas.
 * Falla en voz alta en vez de renovar sin comprobar la cuenta; el puente real
 * lo reemplaza en composición (T007) sin cambiar el servicio.
 */
public class IdentidadSesionPendiente implements ProveedorIdentidadPort {

    @Override
    public DatosIdentidad identidadParaRenovar(UUID usuarioId) {
        throw new DependenciaNoDisponibleException("CuentasPort de Eva y protección de Luis");
    }
}
