package com.marketplace.auth_service.identidad.application.dto;

import java.util.List;
import java.util.Objects;

/**
 * Cliente técnico tal como lo ve la administración (SPEC-19, RF-19.2). No lleva
 * el secreto ni su hash: ninguna lectura los devuelve.
 */
public record ClienteTecnico(String clientId, String nombre, boolean activo, List<ScopeAsignado> scopes) {

    public ClienteTecnico {
        Objects.requireNonNull(clientId, "clientId");
        scopes = List.copyOf(scopes);
    }
}
