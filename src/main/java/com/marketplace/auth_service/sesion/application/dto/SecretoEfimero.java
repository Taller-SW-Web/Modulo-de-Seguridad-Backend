package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;

/**
 * Secreto presentado en el login. Vive una petición, con representación
 * redactada: nunca sale a logs, excepciones ni auditoría.
 */
public record SecretoEfimero(String valor) {
    public SecretoEfimero {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("el secreto es obligatorio");
        }
    }

    @Override
    public String toString() {
        return "SecretoEfimero[redactado]";
    }
}
