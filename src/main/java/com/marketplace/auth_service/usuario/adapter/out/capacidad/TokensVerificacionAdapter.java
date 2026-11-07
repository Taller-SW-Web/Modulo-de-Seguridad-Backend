package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.usuario.application.port.out.TokensVerificacionPort;
import com.marketplace.auth_service.usuario.domain.model.TokenVerificacion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Puente a los tokens de un uso (Juan José) con propósito {@code VERIFICAR_CORREO}. Se busca por
 * SHA-256 en hex del valor claro, el mismo formato con que se guardó; el claro no sale de aquí.
 */
public final class TokensVerificacionAdapter implements TokensVerificacionPort {

    private final TokensUnUsoPort tokens;

    public TokensVerificacionAdapter(TokensUnUsoPort tokens) {
        this.tokens = Objects.requireNonNull(tokens);
    }

    @Override
    public Optional<TokenVerificacion> localizar(String tokenClaro) {
        return tokens.localizar(sha256Hex(tokenClaro), PropositoToken.VERIFICAR_CORREO)
                .map(t -> new TokenVerificacion(t.id(), t.usuarioId(), t.expiraEn(), t.usado()));
    }

    @Override
    public boolean consumir(UUID tokenId, UUID usuarioId, Instant ahora) {
        return tokens.consumir(tokenId, usuarioId, PropositoToken.VERIFICAR_CORREO, ahora);
    }

    static String sha256Hex(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 no está disponible", imposible);
        }
    }
}
