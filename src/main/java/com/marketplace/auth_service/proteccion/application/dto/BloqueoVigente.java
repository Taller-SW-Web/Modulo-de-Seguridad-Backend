package com.marketplace.auth_service.proteccion.application.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Lectura exportada del bloqueo vigente de una cuenta (RF-15.6) para la vista
 * administrativa. {@code hasta} es {@code null} si no vence; {@code motivo} solo
 * existe en un bloqueo manual.
 */
public record BloqueoVigente(String tipo, Instant hasta, String motivo) {
    public BloqueoVigente {
        Objects.requireNonNull(tipo, "tipo");
    }
}
