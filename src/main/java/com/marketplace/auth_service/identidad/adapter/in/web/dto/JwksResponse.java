package com.marketplace.auth_service.identidad.adapter.in.web.dto;

import java.util.List;

/**
 * Esquema {@code Jwks} del contrato. Cada clave lleva solo
 * {@code kty/use/alg/kid/n/e}: el tipo no tiene campos privados que puedan filtrarse.
 */
public record JwksResponse(List<Jwk> keys) {

    public record Jwk(String kty, String use, String alg, String kid, String n, String e) {
    }
}
