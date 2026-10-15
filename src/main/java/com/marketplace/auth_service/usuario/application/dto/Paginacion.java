package com.marketplace.auth_service.usuario.application.dto;

public record Paginacion(int pagina, int tamano) {
    public Paginacion {
        if (pagina < 1 || tamano < 1 || tamano > 100)
            throw new IllegalArgumentException("Página positiva y tamaño entre 1 y 100 requeridos");
    }
    public static Paginacion porDefecto() { return new Paginacion(1,20); }
    public long offset() { return (long) (pagina - 1) * tamano; }
}
