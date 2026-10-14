package com.marketplace.auth_service.usuario.application.dto;

public record PaginaResultado<T>(
    java.util.List<T> elementos,
    int pagina,
    int tamano,
    long total
) {}