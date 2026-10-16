package com.marketplace.auth_service.proteccion.adapter.out.capacidad;

import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.proteccion.application.dto.EnlaceEmitido;
import com.marketplace.auth_service.proteccion.application.dto.TokenDesbloqueo;
import com.marketplace.auth_service.proteccion.application.port.out.TokensProteccionPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Puente a los tokens de un solo uso de Juan José (#20), siempre con propósito DESBLOQUEO_AUTOMATICO. */
public final class TokensProteccionAdapter implements TokensProteccionPort {
    private static final PropositoToken PROPOSITO = PropositoToken.DESBLOQUEO_AUTOMATICO;
    private final TokensUnUsoPort tokens;

    public TokensProteccionAdapter(TokensUnUsoPort tokens) {
        this.tokens = tokens;
    }

    @Override
    public Optional<TokenDesbloqueo> localizar(String hash) {
        return tokens.localizar(hash, PROPOSITO).map(t -> new TokenDesbloqueo(t.id(), t.usuarioId(),
                t.proposito().name(), t.contextoId(), t.expiraEn(), t.usado()));
    }

    @Override
    public EnlaceEmitido emitir(UUID usuarioId, UUID revisionBloqueo, Duration ttl) {
        var emitido = tokens.emitir(usuarioId, PROPOSITO, revisionBloqueo, ttl);
        return new EnlaceEmitido(emitido.id(), emitido.tokenClaro(), emitido.expiraEn());
    }

    @Override
    public boolean consumir(UUID id, UUID usuarioId, UUID revisionBloqueo, Instant ahora) {
        return tokens.consumir(id, usuarioId, PROPOSITO, revisionBloqueo, ahora);
    }

    @Override
    public void invalidarPrevios(UUID usuarioId) {
        tokens.invalidarPrevios(usuarioId, PROPOSITO);
    }
}
