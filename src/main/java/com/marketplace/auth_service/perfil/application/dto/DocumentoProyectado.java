package com.marketplace.auth_service.perfil.application.dto;

import java.util.Objects;

/** Lo único del documento que sale hacia otras capacidades o la interfaz: tipo y enmascarado. */
public record DocumentoProyectado(String tipo, String enmascarado) {
    public DocumentoProyectado {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(enmascarado, "enmascarado");
    }
}
