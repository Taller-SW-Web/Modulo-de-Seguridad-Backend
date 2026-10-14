package com.marketplace.auth_service.usuario.application.dto;

public record Paginacion(
    int pagina,
    int tamano
) {
    public static Paginacion porDefecto() {
        return new Paginacion(1, 20);
    }
    public int offset() {
        return (pagina - 1) * tamano;
    }
}