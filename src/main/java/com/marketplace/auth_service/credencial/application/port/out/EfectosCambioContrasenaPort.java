package com.marketplace.auth_service.credencial.application.port.out;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lo que el cambio de contraseña consume de otras capacidades (cuentas, sesiones, auditoría)
 * mediante su puente, y la transacción que los agrupa.
 */
public interface EfectosCambioContrasenaPort {

    <T> T enTransaccion(Supplier<T> trabajo);

    /** Lock de la cuenta (orden de locks común) antes de tocar la credencial. */
    void bloquearCuenta(UUID usuarioId);

    /** Familia de sesión del acceso que hace el cambio, si sigue vigente. */
    Optional<UUID> familiaDe(UUID usuarioId, UUID jti);

    void revocarOtras(UUID usuarioId, UUID familiaActual);

    void revocarTodas(UUID usuarioId);

    /** {@code CONTRASENA_CAMBIADA}: crítica, en el mismo commit. */
    void auditarCambio(UUID usuarioId, ContextoPeticion contexto);

    /** Aviso cifrado y durable en el mismo commit, sin transporte SMTP bajo locks. */
    void avisarCambio(UUID usuarioId);
}
