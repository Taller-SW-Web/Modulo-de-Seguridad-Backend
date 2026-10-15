package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;

/**
 * Comparación previa de Juan José: coincide o no, con la revisión observada
 * para revalidar bajo lock. Inexistente hace comparación equivalente y nunca
 * coincide; la revisión ficticia no confirma ni autoriza nada.
 */
public record PrevisionCredencial(boolean coincide, RevisionCredencial revision) {
    public PrevisionCredencial {
        Objects.requireNonNull(revision, "revision");
    }
}
