package com.marketplace.auth_service.direccion.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.UUID;

/**
 * Auditoría no crítica del alta: lleva el nombre del campo, nunca los valores de la dirección.
 * {@code moduloConsultoDirecciones} cubre RF-18.6 (FEAT-06-T3): cada lectura de un módulo
 * consumidor con token de servicio.
 */
public interface AuditoriaDireccionesPort {
    void direccionAgregada(ActorUsuario actor, UUID usuarioId, ContextoPeticion contexto);

    void moduloConsultoDirecciones(String clientId, UUID usuarioId);
}
