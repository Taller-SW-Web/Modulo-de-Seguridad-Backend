package com.marketplace.auth_service.mfa.application.dto;

import java.util.Objects;

/** Destinos de entrega del código; solo para el productor del envío, nunca para logs. */
public record ContactoMfa(String correo, String celular) {
    public ContactoMfa {
        Objects.requireNonNull(correo, "correo");
    }

    @Override
    public String toString() {
        return "ContactoMfa[REDACTADO]";
    }
}
