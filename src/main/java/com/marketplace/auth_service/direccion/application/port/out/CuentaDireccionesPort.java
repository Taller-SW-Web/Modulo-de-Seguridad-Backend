package com.marketplace.auth_service.direccion.application.port.out;

import java.util.UUID;

/** Puente de lectura/bloqueo de la cuenta propietaria, sin importar sus entidades. */
public interface CuentaDireccionesPort {
    boolean existe(UUID usuarioId);
    void bloquear(UUID usuarioId);
}
