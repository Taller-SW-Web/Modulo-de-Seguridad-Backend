package com.marketplace.auth_service.direccion.application.port.in;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.direccion.domain.model.DireccionNoEncontradaException;
import com.marketplace.auth_service.shared.domain.ActorServicio;
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

    /**
     * Lectura para un módulo consumidor con token de servicio (SPEC-18 ESC-18.4, FEAT-06-T3): la ruta
     * ya exigió el scope {@code direcciones:leer}, así que aquí no se autoriza, solo se ausculta la
     * cuenta y se audita el acceso (RF-18.6).
     *
     * @throws DireccionNoEncontradaException si la cuenta no existe
     */
    List<DireccionDatos> listarParaServicio(ActorServicio actor, UUID usuarioId);

    record DatosNuevaDireccion(String etiqueta, String departamento, String provincia, String distrito,
            String direccion, String referencia, Boolean esPredeterminada) {}
}
