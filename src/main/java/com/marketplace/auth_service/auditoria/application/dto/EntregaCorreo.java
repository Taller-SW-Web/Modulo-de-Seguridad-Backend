package com.marketplace.auth_service.auditoria.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Correo a enviar (OTP, enlaces, avisos). El adaptador cifra destino y
 * contenido (AES-256-GCM) antes de persistirlo en outbox y purga lo sensible
 * tras la entrega. Nunca se registra en logs ni vuelve en un GET público.
 */
public record EntregaCorreo(UUID mensajeId, String topico, String destino, String contenido, Plantilla plantilla) {
    public EntregaCorreo(UUID mensajeId, String topico, String destino, String contenido) {
        this(mensajeId, topico, destino, contenido, null);
    }

    public EntregaCorreo {
        Objects.requireNonNull(mensajeId, "mensajeId");
        Objects.requireNonNull(topico, "topico");
        Objects.requireNonNull(destino, "destino");
        Objects.requireNonNull(contenido, "contenido");
    }

    public record Plantilla(TipoPlantilla tipo, String enlace) {
        public Plantilla {
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(enlace, "enlace");
        }
    }

    public enum TipoPlantilla {
        VERIFICACION_MARKETPLACE,
        RECUPERACION_MARKETPLACE,
        OTP_INICIO_SESION
    }
}
