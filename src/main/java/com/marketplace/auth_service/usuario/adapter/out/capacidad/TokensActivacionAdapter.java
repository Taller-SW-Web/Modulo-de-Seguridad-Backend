package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.usuario.application.port.out.TokensActivacionPort;
import com.marketplace.auth_service.usuario.domain.model.TokenVerificacion;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puente a los tokens de un uso (Juan José) con propósito {@code ACTIVACION}. Se busca por SHA-256 en hex
 * del valor claro, como en la verificación de correo; el claro no sale de aquí.
 */
public final class TokensActivacionAdapter implements TokensActivacionPort {

    private final TokensUnUsoPort tokens;

    public TokensActivacionAdapter(TokensUnUsoPort tokens) {
        this.tokens = Objects.requireNonNull(tokens);
    }

    @Override
    public Optional<TokenVerificacion> localizar(String tokenClaro) {
        return tokens.localizar(TokensVerificacionAdapter.sha256Hex(tokenClaro), PropositoToken.ACTIVACION)
                .map(t -> new TokenVerificacion(t.id(), t.usuarioId(), t.expiraEn(), t.usado()));
    }

    @Override
    public boolean consumir(UUID tokenId, UUID usuarioId, Instant ahora) {
        return tokens.consumir(tokenId, usuarioId, PropositoToken.ACTIVACION, ahora);
    }
}
