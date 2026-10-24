package com.marketplace.auth_service.direccion.application.port.in;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.List;
import java.util.UUID;

/**
 * Lectura y alta de direcciones: el titular, o quien tenga el privilegio vigente que fija el contrato
 * ({@code usuario.ver} para leer, {@code ADMIN_SISTEMA} para añadir).
 */
public interface DireccionesPort {
    List<DireccionDatos> listar(ActorUsuario actor, UUID usuarioId);
    DireccionDatos crear(ActorUsuario actor, UUID usuarioId, DatosNuevaDireccion datos, ContextoPeticion contexto);

    record DatosNuevaDireccion(String etiqueta, String departamento, String provincia, String distrito,
            String direccion, String referencia, Boolean esPredeterminada) {}
}
