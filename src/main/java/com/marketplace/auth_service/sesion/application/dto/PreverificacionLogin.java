package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;
import java.util.Optional;

/** Resultado de comparar la contraseña fuera de locks (Juan José, comparación equivalente si no existe). */
public record PreverificacionLogin(boolean coincide, Optional<RevisionLogin> revision) {
    public PreverificacionLogin {
        Objects.requireNonNull(revision, "revision");
        if (coincide && revision.isEmpty()) {
            throw new IllegalArgumentException("Falta revisión");
        }
    }
}
