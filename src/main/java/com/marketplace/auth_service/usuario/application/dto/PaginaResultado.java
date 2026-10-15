package com.marketplace.auth_service.usuario.application.dto;

import java.util.List;

public record PaginaResultado<T>(List<T> elementos, int pagina, int tamano, long total) {
    public PaginaResultado { elementos = List.copyOf(elementos); }
}
