package com.marketplace.auth_service.credencial.application.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** El valor claro solo se entrega al productor que solicitará su envío. */
public record TokenEmitido(UUID id, String tokenClaro, Instant expiraEn) {
    public TokenEmitido { Objects.requireNonNull(id); Objects.requireNonNull(tokenClaro); Objects.requireNonNull(expiraEn); }
    @Override public String toString() { return "TokenEmitido[id=" + id + ", expiraEn=" + expiraEn + ", REDACTADO]"; }
}
