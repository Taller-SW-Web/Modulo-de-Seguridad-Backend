package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.usuario.domain.model.TokenVerificacion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Tokens de un solo uso del enlace de {@code POST /auth/verificar-correo} (Juan José):
 * {@code VERIFICAR_CORREO} (cuenta nueva) y {@code CAMBIO_CORREO} (SPEC-16). {@code consumir*} requieren
 * transacción.
 */
public interface TokensVerificacionPort {

    /** Busca por valor claro; el token localizado dice si es de cambio de correo ({@code destinoCorreo}). */
    Optional<TokenVerificacion> localizar(String tokenClaro);

    /** {@code false} si otro lo consumió antes, venció o ya no es de esa cuenta. Solo {@code VERIFICAR_CORREO}. */
    boolean consumir(UUID tokenId, UUID usuarioId, Instant ahora);

    /** Igual que {@link #consumir} pero para un token {@code CAMBIO_CORREO}. */
    boolean consumirCambioCorreo(UUID tokenId, UUID usuarioId, Instant ahora);
}
