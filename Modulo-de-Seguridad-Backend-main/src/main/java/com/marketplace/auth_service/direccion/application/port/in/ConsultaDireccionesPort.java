package com.marketplace.auth_service.direccion.application.port.in;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import java.util.List;
import java.util.UUID;

/**
 * Contrato exportado de solo lectura para que otras capacidades consulten direcciones
 * sin conocer repositorios ni entidades JPA (FEAT-04-T2; lo consume FEAT-06, {@code direcciones:leer}).
 *
 * <p>No autoriza: el consumidor valida antes el scope {@code direcciones:leer} o la titularidad
 * y decide si la cuenta existe (aquí una cuenta sin direcciones y una inexistente dan lo mismo).
 */
public interface ConsultaDireccionesPort {
    /**
     * Direcciones del usuario, con la predeterminada primero. Lista vacía si no tiene; la lista
     * devuelta no es modificable.
     *
     * @throws NullPointerException si {@code usuarioId} es nulo
     */
    List<DireccionDatos> consultar(UUID usuarioId);
}
