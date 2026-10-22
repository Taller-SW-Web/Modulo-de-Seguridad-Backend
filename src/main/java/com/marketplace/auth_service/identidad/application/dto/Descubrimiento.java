package com.marketplace.auth_service.identidad.application.dto;

import java.util.List;

/**
 * Metadatos del emisor (RF-17.2): issuer, JWKS, algoritmo de firma y, desde
 * FEAT-05, los endpoints de token de servicio e introspección con los scopes
 * de esta API.
 */
public record Descubrimiento(String issuer, String jwksUri, List<String> algoritmosFirma,
                             String tokenEndpoint, String introspectionEndpoint,
                             List<String> grantTypes, List<String> scopes) {

    public Descubrimiento {
        algoritmosFirma = List.copyOf(algoritmosFirma);
        grantTypes = List.copyOf(grantTypes);
        scopes = List.copyOf(scopes);
    }
}
