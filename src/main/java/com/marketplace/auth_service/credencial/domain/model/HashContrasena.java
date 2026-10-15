package com.marketplace.auth_service.credencial.domain.model;

import java.util.Objects;

public final class HashContrasena {
    private final String valor;
    private final AlgoritmoHash algoritmo;

    public HashContrasena(String valor, AlgoritmoHash algoritmo) {
        this.valor = Objects.requireNonNull(valor, "valor");
        if (valor.isBlank()) {
            throw new IllegalArgumentException("El hash no puede estar vacío");
        }
        this.algoritmo = Objects.requireNonNull(algoritmo, "algoritmo");
    }

    public String valor() {
        return valor;
    }

    public AlgoritmoHash algoritmo() {
        return algoritmo;
    }

    @Override
    public String toString() {
        return "HashContrasena[REDACTADO]";
    }
}
