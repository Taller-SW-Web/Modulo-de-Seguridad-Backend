package com.marketplace.auth_service.perfil.application.dto;

import java.util.Objects;

/** Documento en claro, solo para el consumidor externo que tiene el scope que lo autoriza. */
public record DocumentoClaro(String tipo, String valor) {
    public DocumentoClaro {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(valor, "valor");
    }
}
