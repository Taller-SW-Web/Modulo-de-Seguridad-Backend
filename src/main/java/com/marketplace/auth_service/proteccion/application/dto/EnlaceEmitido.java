package com.marketplace.auth_service.proteccion.application.dto;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record EnlaceEmitido(UUID id, String secreto, Instant expiraEn) {
    public EnlaceEmitido { Objects.requireNonNull(id); Objects.requireNonNull(secreto); Objects.requireNonNull(expiraEn); }
    @Override public String toString() { return "EnlaceEmitido[id=" + id + ", REDACTADO]"; }
}
