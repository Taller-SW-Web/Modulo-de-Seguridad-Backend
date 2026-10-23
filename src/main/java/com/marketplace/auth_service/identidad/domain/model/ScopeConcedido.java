package com.marketplace.auth_service.identidad.domain.model;

import java.util.Objects;

/**
 * Scope concedido a un cliente con la API dueña que lo valida (A4). La
 * audiencia sale de la tabla {@code scope}; no se deduce del código.
 */
public record ScopeConcedido(String codigo, String audiencia) {

    public ScopeConcedido {
        Objects.requireNonNull(codigo, "codigo");
        Objects.requireNonNull(audiencia, "audiencia");
    }
}
