package com.marketplace.auth_service.sesion.application.port.out;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;

import java.time.Instant;
import java.util.UUID;

/**
 * Efecto no crítico del cierre de sesión (FEAT-12-T2, SPEC-06 RF-06.5 y
 * SPEC-12): el hecho {@code SESION_CERRADA} viaja al puerto de auditoría de
 * Christian (EP-06) y su fallo nunca revierte el {@code 204}: el cierre de la
 * familia ya está confirmado.
 *
 * <p>Se registra solo desde {@code CierreResultado.Cerrada}, que es la única
 * variante con identidad real: un refresco desconocido o ya revocado nunca
 * audita una identidad inventada. Lleva ip y agente de la petición; nunca el
 * token de acceso, el refresco ni ningún otro secreto (RF-12.5).
 */
public interface EfectosCierrePort {

    /** Hecho no crítico {@code SESION_CERRADA}: se intenta después del commit, con deduplicación. */
    void auditarCierre(UUID usuarioId, UUID familiaId, ContextoPeticion contexto, Instant ahora);
}
