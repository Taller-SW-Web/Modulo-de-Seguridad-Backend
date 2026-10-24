package com.marketplace.auth_service.identidad.application.dto;

import java.util.Objects;

/** Documento proyectado para SPEC-18: el mismo tipo tanto en claro como enmascarado. */
public record DocumentoIdentidad(String tipo, String valor) {
    public DocumentoIdentidad {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(valor, "valor");
    }
}
