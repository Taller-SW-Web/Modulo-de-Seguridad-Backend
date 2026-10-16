package com.marketplace.auth_service.identidad.application.dto;

import java.util.List;

/**
 * Metadatos del emisor (RF-17.2). En H3 solo se declara lo que existe:
 * issuer, JWKS y algoritmo de firma. {@code token_endpoint},
 * {@code introspection_endpoint} y los scopes se añadirán cuando se implementen
 * los tokens de servicio y la introspección (SPEC-17, incremento posterior).
 */
public record Descubrimiento(String issuer, String jwksUri, List<String> algoritmosFirma) {

    public Descubrimiento {
        algoritmosFirma = List.copyOf(algoritmosFirma);
    }
}
