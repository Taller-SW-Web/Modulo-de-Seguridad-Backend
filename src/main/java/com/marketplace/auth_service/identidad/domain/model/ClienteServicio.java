package com.marketplace.auth_service.identidad.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Módulo consumidor registrado (SPEC-17, tabla {@code cliente_servicio}). Lo
 * aprovisiona operaciones (Christian) con el catálogo del PO; el runtime solo
 * lo lee. El secreto llega únicamente como hash: nunca en claro.
 */
public record ClienteServicio(String clientId, String secretoHash, boolean activo, List<ScopeConcedido> scopes) {

    public ClienteServicio {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(secretoHash, "secretoHash");
        scopes = List.copyOf(scopes);
    }

    /** Códigos concedidos, ordenados y sin repetir (RF-17.5). */
    public List<String> codigosScopes() {
        return scopes.stream().map(ScopeConcedido::codigo).distinct().sorted().toList();
    }

    /** {@code aud}: las APIs dueñas de los scopes concedidos, sin repetir (RF-17.12). */
    public List<String> audiencias() {
        return scopes.stream().map(ScopeConcedido::audiencia).distinct().sorted().toList();
    }

    @Override
    public String toString() {
        return "ClienteServicio[clientId=" + clientId + ", activo=" + activo + ", scopes=" + codigosScopes() + "]";
    }
}
