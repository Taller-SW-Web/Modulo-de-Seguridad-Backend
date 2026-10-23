package com.marketplace.auth_service.credencial.application.port.in;

import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.dto.TokenEmitido;
import com.marketplace.auth_service.credencial.application.dto.TokenUnUsoSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Capacidad exportada a los productores de enlaces de un solo uso. */
public interface TokensUnUsoPort {
    Optional<TokenUnUsoSnapshot> localizar(String hash, PropositoToken proposito);
    TokenEmitido emitir(UUID usuarioId, PropositoToken proposito, Duration ttl);
    /** Emite la confirmación del nuevo correo; el destino solo se comparte entre productores internos. */
    TokenEmitido emitirCambioCorreo(UUID usuarioId, String destino);
    /** El desbloqueo necesita ligar el token a la revisión UUID del bloqueo de Luis. */
    TokenEmitido emitir(UUID usuarioId, PropositoToken proposito, UUID contextoId, Duration ttl);
    boolean consumir(UUID id, UUID usuarioId, PropositoToken proposito, Instant ahora);
    /** Consumidores con contexto (Luis) deben presentar la revisión actual dentro de su transacción. */
    boolean consumir(UUID id, UUID usuarioId, PropositoToken proposito, UUID contextoId, Instant ahora);
    void invalidarPrevios(UUID usuarioId, PropositoToken proposito);
}
