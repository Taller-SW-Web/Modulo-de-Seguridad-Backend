package com.marketplace.auth_service.perfil.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Puente de lectura y bloqueo de la cuenta, sin importar sus entidades. */
public interface CuentaCorreoPort {

    record CuentaCorreo(UUID id, String correo) {}

    Optional<CuentaCorreo> leer(UUID usuarioId);

    /** Serializa solicitudes concurrentes de la misma cuenta. */
    void bloquear(UUID usuarioId);

    /** ¿Alguna cuenta usa ya este correo (ya normalizado)? */
    boolean correoEnUso(String correoNormalizado);
}
