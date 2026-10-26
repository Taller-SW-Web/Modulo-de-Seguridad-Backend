package com.marketplace.auth_service.perfil.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Efectos de una edición de atributos que salen de perfil: evento público
 * {@code usuario.atributos_actualizados} (outbox, mismo commit) y auditoría
 * {@code ATRIBUTOS_ACTUALIZADOS} (no crítica, tras el commit). Los dueños son Christian (EP-06).
 * Lleva los NOMBRES de los campos que cambiaron, nunca sus valores (RF-16.9, Ley 29733).
 */
public interface EfectosPerfilPort {
    /** @param campos nombres de campos realmente modificados (no vacío) */
    void atributosActualizados(UUID actorId, UUID usuarioId, List<String> campos);
}
