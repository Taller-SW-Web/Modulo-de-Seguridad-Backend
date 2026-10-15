package com.marketplace.auth_service.credencial.application.port.out;

import com.marketplace.auth_service.credencial.domain.model.PropositoTokenUnUso;
import com.marketplace.auth_service.credencial.domain.model.TokenUnUso;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistencia exclusiva de Juan José; los mutadores participan en la transacción del productor. */
public interface TokenUnUsoRepositoryPort {
    void guardar(TokenUnUso token);
    Optional<TokenUnUso> buscar(String hash, PropositoTokenUnUso proposito);
    Optional<TokenUnUso> bloquear(UUID id);
    void invalidarPrevios(UUID usuarioId, PropositoTokenUnUso proposito, Instant ahora);
    void marcarUsado(UUID id);
}
