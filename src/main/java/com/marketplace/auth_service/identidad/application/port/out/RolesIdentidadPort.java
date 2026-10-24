package com.marketplace.auth_service.identidad.application.port.out;

import java.util.List;
import java.util.UUID;

/** Puente hacia los roles actuales de la cuenta (SPEC-18): el dueño es {@code autorizacion}. */
public interface RolesIdentidadPort {
    List<String> rolesDe(UUID usuarioId);
}
