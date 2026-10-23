package com.marketplace.auth_service.usuario.application.dto;

/** Omitido conserva; presente distingue null de valor. */
public record CampoBasico<T>(boolean presente, T valor) {
    public CampoBasico {
        if (!presente && valor != null) throw new IllegalArgumentException("Campo omitido con valor");
    }
    public static <T> CampoBasico<T> omitido() { return new CampoBasico<>(false, null); }
    public static <T> CampoBasico<T> presente(T valor) { return new CampoBasico<>(true, valor); }
}
