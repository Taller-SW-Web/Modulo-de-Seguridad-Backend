package com.marketplace.auth_service.identidad.application.dto;

import java.util.Objects;

/**
 * Clave pública RSA de verificación, tal como se publica en el JWKS: solo el
 * {@code kid} y los parámetros públicos {@code n} y {@code e} en base64url.
 * Uso ({@code sig}) y algoritmo ({@code RS256}) son fijos en H3.
 */
public record ClavePublica(String kid, String n, String e) {

    public static final String USO = "sig";
    public static final String ALGORITMO = "RS256";

    public ClavePublica {
        Objects.requireNonNull(kid, "kid");
        Objects.requireNonNull(n, "n");
        Objects.requireNonNull(e, "e");
    }
}
