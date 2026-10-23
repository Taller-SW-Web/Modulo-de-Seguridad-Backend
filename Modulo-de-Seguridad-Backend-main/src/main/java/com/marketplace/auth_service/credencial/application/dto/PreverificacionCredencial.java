package com.marketplace.auth_service.credencial.application.dto;

import java.util.Objects;
import java.util.Optional;

/** La revisión de una credencial existente también acompaña un no-coincide:
 * el coordinador puede revalidar antes de registrar un fallo de contraseña. */
public record PreverificacionCredencial(boolean coincide, Optional<RevisionCredencial> revision) {
    public PreverificacionCredencial {
        Objects.requireNonNull(revision, "revision");
        if (coincide && revision.isEmpty()) throw new IllegalArgumentException("Falta revisión");
    }
}
