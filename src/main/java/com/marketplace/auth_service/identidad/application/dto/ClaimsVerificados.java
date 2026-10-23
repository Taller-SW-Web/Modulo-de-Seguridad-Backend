package com.marketplace.auth_service.identidad.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Claims de un token cuya firma, algoritmo y {@code kid} ya comprobó el
 * adaptador criptográfico. El resto de reglas (issuer, vigencia, perfil) las
 * aplica el caso de uso. Un claim ausente o de tipo inesperado llega como {@code null}.
 *
 * <p>{@code iat}, {@code nbf} y {@code exp} se leen tal como vienen en el token:
 * nunca se completan con valores supuestos, así que un {@code iat} ausente llega
 * como {@code null} y se rechaza.
 *
 * <p>{@code aud} y {@code scope} solo los usa el perfil de servicio (SPEC-17
 * RF-17.12/17.13); un acceso de persona no los lleva.
 */
public record ClaimsVerificados(
        String iss,
        String tipo,
        String sub,
        String jti,
        String email,
        List<String> roles,
        List<String> permisos,
        Instant iat,
        Instant nbf,
        Instant exp,
        List<String> aud,
        String scope) {

    /** Claims de un acceso de persona, sin audiencia ni scope. */
    public ClaimsVerificados(String iss, String tipo, String sub, String jti, String email,
                             List<String> roles, List<String> permisos, Instant iat, Instant nbf, Instant exp) {
        this(iss, tipo, sub, jti, email, roles, permisos, iat, nbf, exp, null, null);
    }
}
