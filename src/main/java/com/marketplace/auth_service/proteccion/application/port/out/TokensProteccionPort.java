package com.marketplace.auth_service.proteccion.application.port.out;
import com.marketplace.auth_service.proteccion.application.dto.*;
import java.time.*;
import java.util.*;
/** Salida de protección. Su puente deberá consumir el TokensUnUsoPort de #20;
 * este bloque no implementa ni escribe token_un_uso. */
public interface TokensProteccionPort {
    Optional<TokenDesbloqueo> localizar(String hash);
    EnlaceEmitido emitir(UUID usuarioId, UUID revisionBloqueo, Duration ttl);
    boolean consumir(UUID id, UUID usuarioId, UUID revisionBloqueo, Instant ahora);
    void invalidarPrevios(UUID usuarioId);
}
