package com.marketplace.auth_service.identidad.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Documento {@code openid-configuration} del contrato, con los nombres de campo
 * estándar que leen Spring Security ({@code issuer-uri}) y .NET JwtBearer.
 */
public record DescubrimientoResponse(
        String issuer,
        @JsonProperty("jwks_uri") String jwksUri,
        @JsonProperty("token_endpoint") String tokenEndpoint,
        @JsonProperty("introspection_endpoint") String introspectionEndpoint,
        @JsonProperty("id_token_signing_alg_values_supported") List<String> idTokenSigningAlgValuesSupported,
        @JsonProperty("grant_types_supported") List<String> grantTypesSupported,
        @JsonProperty("scopes_supported") List<String> scopesSupported) {
}
