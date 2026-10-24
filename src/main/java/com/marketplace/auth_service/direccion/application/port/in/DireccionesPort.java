package com.marketplace.auth_service.direccion.application.port.in;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.List;
import java.util.UUID;

/** Casos de uso de lectura y alta de direcciones propias. */
public interface DireccionesPort {
    List<DireccionDatos> listar(ActorUsuario actor, UUID usuarioId);
    DireccionDatos crear(ActorUsuario actor, UUID usuarioId, DatosNuevaDireccion datos);

    record DatosNuevaDireccion(String etiqueta, String departamento, String provincia, String distrito,
            String direccion, String referencia, Boolean esPredeterminada) {}
}
