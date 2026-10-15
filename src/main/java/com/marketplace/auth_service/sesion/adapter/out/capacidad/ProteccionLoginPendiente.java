package com.marketplace.auth_service.sesion.adapter.out.capacidad;

import com.marketplace.auth_service.sesion.application.dto.SecretoEfimero;
import com.marketplace.auth_service.sesion.application.port.out.ProteccionLoginPort;
import com.marketplace.auth_service.sesion.domain.exception.DependenciaNoDisponibleException;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.util.UUID;

/**
 * Marcador hasta que Luis entregue {@code ProteccionPort}: evaluación,
 * registro de fallos y reset son suyos (SPEC-14). El puente real lo reemplaza
 * en composición (T007).
 */
public class ProteccionLoginPendiente implements ProteccionLoginPort {

    @Override
    public boolean loginBloqueado(UUID usuarioId) {
        throw new DependenciaNoDisponibleException("ProteccionPort de Luis");
    }

    @Override
    public void registrarFallo(UUID usuarioId, SecretoEfimero intento, ContextoPeticion contexto) {
        throw new DependenciaNoDisponibleException("ProteccionPort de Luis");
    }

    @Override
    public void completarLogin(UUID usuarioId) {
        throw new DependenciaNoDisponibleException("ProteccionPort de Luis");
    }
}
