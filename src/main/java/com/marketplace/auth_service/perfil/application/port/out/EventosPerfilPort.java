package com.marketplace.auth_service.perfil.application.port.out;

import java.util.List;
import java.util.UUID;

/** {@code usuario.atributos_actualizados} en el outbox, dentro de la misma transacción. Solo nombres de campos. */
public interface EventosPerfilPort {
    void atributosActualizados(UUID usuarioId, List<String> campos);
}
