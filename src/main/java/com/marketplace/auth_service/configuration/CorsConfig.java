package com.marketplace.auth_service.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * CORS para la SPA propia desplegada en otro origen (TEC-INFRA-T005, hito-3-ventas §CORS).
 *
 * <p>Solo los orígenes acordados de {@code G7_CORS_ORIGENES} (lista separada por comas,
 * p. ej. {@code https://g7-seguridad.vercel.app}); sin valor no se admite ningún origen
 * cruzado. Nunca {@code *}: el acceso viaja en {@code Authorization}. Sin credenciales
 * de navegador (no hay cookies) y con {@code Retry-After} expuesta para las pantallas
 * de límite y bloqueo. Los consumidores servidor a servidor (G5) no usan CORS.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${g7.cors.origenes:}") String origenes) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origenesAcordados(origenes));
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cors.setExposedHeaders(List.of("Retry-After", "Location"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofMinutes(10));
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", cors);
        return fuente;
    }

    /** Orígenes exactos {@code esquema://host[:puerto]}; rechaza comodines y rutas al arrancar. */
    static List<String> origenesAcordados(String origenes) {
        return Arrays.stream(origenes.split(","))
                .map(String::strip)
                .filter(origen -> !origen.isEmpty())
                .peek(CorsConfig::exigirOrigenExacto)
                .toList();
    }

    private static void exigirOrigenExacto(String origen) {
        URI uri;
        try {
            uri = URI.create(origen);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Origen CORS inválido: " + origen, e);
        }
        boolean exacto = ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                && uri.getHost() != null && !origen.contains("*")
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
        if (!exacto) {
            throw new IllegalStateException("Origen CORS inválido (debe ser esquema://host[:puerto], sin * ni ruta): "
                    + origen);
        }
    }
}
