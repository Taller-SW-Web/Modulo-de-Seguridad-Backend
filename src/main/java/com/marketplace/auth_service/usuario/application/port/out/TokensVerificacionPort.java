package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.usuario.domain.model.TokenVerificacion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Tokens {@code VERIFICAR_CORREO} de un solo uso (Juan José). {@code consumir} requiere transacción. */
public interface TokensVerificacionPort {
    Optional<TokenVerificacion> localizar(String tokenClaro);

    /** {@code false} si otro lo consumió antes, venció o ya no es de esa cuenta. */
    boolean consumir(UUID tokenId, UUID usuarioId, Instant ahora);
}
