package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;

/**
 * Refresco presentado para rotar (SPEC-06). Vive una petición, con
 * representación redactada: nunca sale a logs ni a excepciones.
 */
public record SecretoRefresco(String valor) {
    public SecretoRefresco {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("el refresco es obligatorio");
        }
    }

    @Override
    public String toString() {
        return "SecretoRefresco[redactado]";
    }
}
