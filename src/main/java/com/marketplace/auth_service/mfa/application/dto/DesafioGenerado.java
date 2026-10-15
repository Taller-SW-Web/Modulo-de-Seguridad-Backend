package com.marketplace.auth_service.mfa.application.dto;

import java.util.Objects;

/** El token solo existe durante la petición que crea el desafío. No serializar este DTO como HTTP. */
public record DesafioGenerado(String token, String hashToken) {
    public DesafioGenerado {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(hashToken, "hashToken");
    }

    @Override
    public String toString() {
        return "DesafioGenerado[token=REDACTADO, hashToken=REDACTADO]";
    }
}
