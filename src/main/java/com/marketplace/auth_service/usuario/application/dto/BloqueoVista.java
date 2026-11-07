package com.marketplace.auth_service.usuario.application.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Bloqueo vigente en la vista administrativa (esquema {@code Usuario.bloqueo}, RF-15.6).
 *
 * @param tipo   {@code AUTOMATICO} o {@code MANUAL}
 * @param hasta  vencimiento; {@code null} si no vence
 * @param motivo solo en un bloqueo manual
 */
public record BloqueoVista(String tipo, Instant hasta, String motivo) {
    public BloqueoVista {
        Objects.requireNonNull(tipo, "tipo");
    }
}
