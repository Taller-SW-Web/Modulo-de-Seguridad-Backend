package com.marketplace.auth_service.identidad.application.port.out;

import com.marketplace.auth_service.identidad.application.dto.CuentaIdentidad;
import java.util.Optional;
import java.util.UUID;

/** Puente hacia la cuenta básica (SPEC-18): el dueño físico de la tabla es {@code usuario}. */
public interface CuentasIdentidadPort {
    Optional<CuentaIdentidad> leer(UUID usuarioId);
}
