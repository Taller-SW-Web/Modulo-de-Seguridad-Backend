package com.marketplace.auth_service.credencial.application.port.out;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/** Coordinación transaccional con cuentas, protección, sesiones, auditoría y outbox. */
public interface EfectosRestablecimientoPort {
    <T> T enTransaccion(Supplier<T> trabajo);
    void bloquearCuenta(UUID usuarioId);
    void prepararProteccion(UUID usuarioId);
    void restablecerProteccion(UUID usuarioId, ContextoPeticion contexto, Instant ahora);
    void revocarSesiones(UUID usuarioId);
    void auditar(UUID usuarioId, ContextoPeticion contexto, Instant ahora);
    void avisar(UUID usuarioId);
}
