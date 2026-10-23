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
        Instant exp) {
}
