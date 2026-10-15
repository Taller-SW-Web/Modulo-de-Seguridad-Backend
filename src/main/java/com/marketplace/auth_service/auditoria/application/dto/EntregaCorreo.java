package com.marketplace.auth_service.auditoria.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Correo a enviar (OTP, enlaces, avisos). El adaptador cifra destino y
 * contenido (AES-256-GCM) antes de persistirlo en outbox y purga lo sensible
 * tras la entrega. Nunca se registra en logs ni vuelve en un GET público.
 */
public record EntregaCorreo(UUID mensajeId, String topico, String destino, String contenido) {
    public EntregaCorreo {
        Objects.requireNonNull(mensajeId, "mensajeId");
        Objects.requireNonNull(topico, "topico");
        Objects.requireNonNull(destino, "destino");
        Objects.requireNonNull(contenido, "contenido");
    }
}
