package com.marketplace.auth_service.credencial.application.port.in;

import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import java.util.UUID;

/**
 * Primera credencial de una cuenta creada por el registro público (SPEC-01, FEAT-01-T1).
 * El llamador ya validó la política con {@link CredencialesPort#validarNueva}. Requiere su
 * transacción.
 */
public interface CredencialInicialInterna {
    /**
     * Guarda el hash del secreto como credencial actual y primera entrada del historial.
     * Lanza {@link IllegalStateException} si la cuenta ya tiene credencial.
     */
    void crear(UUID usuarioId, SecretoEfimero secreto);
}
