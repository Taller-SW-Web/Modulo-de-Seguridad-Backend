package com.marketplace.auth_service.identidad.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Claims de un token cuya firma, algoritmo y {@code kid} ya comprobó el
 * adaptador criptográfico. El resto de reglas (issuer, vigencia, perfil) las
 * aplica el caso de uso. Un claim ausente o de tipo inesperado llega como {@code null}.
 */
public record ClaimsVerificados(
        String iss,
        String tipo,
        String sub,
        String jti,
        List<String> roles,
        List<String> permisos,
        Instant exp) {
}
