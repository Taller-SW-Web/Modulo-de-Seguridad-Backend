package com.marketplace.auth_service.perfil.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.List;
import java.util.UUID;

/** {@code ATRIBUTOS_ACTUALIZADOS} no crítico tras el commit (SPEC-16 RF-16.10). Nunca valores. */
public interface AuditoriaPerfilPort {
    void atributosActualizados(ActorUsuario actor, UUID usuarioId, List<String> campos, ContextoPeticion contexto);
}
