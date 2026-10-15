package com.marketplace.auth_service.proteccion.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Bloqueo(UUID usuarioId, TipoBloqueo tipo, Instant bloqueadoHasta,
                      String motivo, Instant creadoEn, UUID revision) {
    public Bloqueo {
        Objects.requireNonNull(usuarioId);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(creadoEn);
        Objects.requireNonNull(revision);
        if (tipo == TipoBloqueo.MANUAL && (motivo == null || motivo.isBlank()
                || motivo.length() > 500 || bloqueadoHasta != null)) {
            throw new IllegalArgumentException("Bloqueo manual inválido");
        }
        if (tipo == TipoBloqueo.AUTOMATICO && (motivo != null
                || bloqueadoHasta != null && !bloqueadoHasta.isAfter(creadoEn))) {
            throw new IllegalArgumentException("Bloqueo automático inválido");
        }
    }
    /** Igualdad con hasta implica vencimiento; no requiere job ni mutación. */
    public boolean vigenteEn(Instant ahora) {
        Objects.requireNonNull(ahora);
        return bloqueadoHasta == null || ahora.isBefore(bloqueadoHasta);
    }
    @Override public String toString() {
        return "Bloqueo[usuarioId=" + usuarioId + ", tipo=" + tipo + ", revision=" + revision + "]";
    }
}
