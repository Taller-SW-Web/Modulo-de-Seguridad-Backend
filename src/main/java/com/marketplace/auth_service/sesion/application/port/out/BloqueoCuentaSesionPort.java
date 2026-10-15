package com.marketplace.auth_service.sesion.application.port.out;
import java.util.UUID;
/** Cuenta antes de raíces; el coordinador consume al dueño, sin SQL ajeno. */
public interface BloqueoCuentaSesionPort {
    void bloquear(UUID usuarioId);
}
