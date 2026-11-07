package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.time.Instant;
import java.util.UUID;

/** Estado base de una cuenta ya bloqueada por el llamador. Requiere su transacción. */
public interface EstadoCuentaPort {
    void cambiar(UUID usuarioId, EstadoCuenta estado, Instant ahora);
}
