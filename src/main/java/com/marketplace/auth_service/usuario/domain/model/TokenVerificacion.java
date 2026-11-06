package com.marketplace.auth_service.usuario.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Token de verificación localizado por su hash; nunca lleva el valor claro ni el hash.
 *
 * <p>{@code destinoCorreo} solo viene en los tokens {@code CAMBIO_CORREO} (SPEC-16): es la dirección
 * nueva a la que quedó ligado el enlace. En un token {@code VERIFICAR_CORREO} es {@code null}.
 */
public record TokenVerificacion(UUID id, UUID usuarioId, Instant expiraEn, boolean usado, String destinoCorreo) {
    public TokenVerificacion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(expiraEn, "expiraEn");
    }

    /** Token de verificación de una cuenta nueva (sin dirección de destino). */
    public TokenVerificacion(UUID id, UUID usuarioId, Instant expiraEn, boolean usado) {
        this(id, usuarioId, expiraEn, usado, null);
    }

    public boolean vigenteEn(Instant instante) {
        return instante.isBefore(expiraEn);
    }

    /** {@code true} si el enlace confirma un cambio de correo y no la verificación de una cuenta nueva. */
    public boolean esCambioCorreo() {
        return destinoCorreo != null;
    }

    @Override
    public String toString() {
        return "TokenVerificacion[id=" + id + ", cambioCorreo=" + esCambioCorreo() + ", REDACTADO]";
    }
}
