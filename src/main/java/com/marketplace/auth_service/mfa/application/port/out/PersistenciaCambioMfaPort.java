package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.mfa.domain.model.CambioMfa;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PersistenciaCambioMfaPort {
    Optional<CambioMfa> buscarPorHash(String hash);
    Optional<CambioMfa> bloquear(UUID id);
    void guardar(CambioMfa cambio);
    void invalidarPendientes(UUID usuarioId, Instant ahora);
}
