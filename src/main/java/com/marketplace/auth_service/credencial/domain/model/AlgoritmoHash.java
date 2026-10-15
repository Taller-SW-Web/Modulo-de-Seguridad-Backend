package com.marketplace.auth_service.credencial.domain.model;

public enum AlgoritmoHash {
    ARGON2ID,
    BCRYPT;

    public static AlgoritmoHash desdeHashCodificado(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El hash codificado es obligatorio");
        }
        if (valor.startsWith("$argon2id$")) {
            return ARGON2ID;
        }
        if (valor.startsWith("$2a$") || valor.startsWith("$2b$") || valor.startsWith("$2y$")) {
            return BCRYPT;
        }
        throw new IllegalArgumentException("El formato del hash no está reconocido");
    }
}
