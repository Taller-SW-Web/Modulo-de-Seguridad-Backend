package com.marketplace.auth_service.direccion.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.UUID;

/** Auditoría no crítica del alta: lleva el nombre del campo, nunca los valores de la dirección. */
public interface AuditoriaDireccionesPort {
    void direccionAgregada(ActorUsuario actor, UUID usuarioId, ContextoPeticion contexto);
}
