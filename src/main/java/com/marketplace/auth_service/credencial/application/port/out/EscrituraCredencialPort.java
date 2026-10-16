package com.marketplace.auth_service.credencial.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Reemplaza el hash vigente (versión + 1) y lo anota en el historial. Requiere la transacción del caso de uso. */
public interface EscrituraCredencialPort {
    void reemplazar(UUID usuarioId, String hashNuevo, Instant ahora);
}
