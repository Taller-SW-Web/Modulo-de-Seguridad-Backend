package com.marketplace.auth_service.auditoria.application.dto;

import java.util.List;
import java.util.Objects;

/** Página de actividad propia: total real antes de proyectar (EP-06-T004). */
public record PaginaActividad(List<RegistroSeguro> registros, long total) {
    public PaginaActividad {
        Objects.requireNonNull(registros, "registros");
        registros = List.copyOf(registros);
    }
}
