package com.marketplace.auth_service.shared.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.Objects;
import java.util.function.Function;

/**
 * Plantilla de la ruta pedida entre las que declara el backend ({@code /api/v1/usuarios/{id}}).
 * Hace falta cuando Spring no llegó a elegir un controlador, por ejemplo un 401 de la cadena de
 * seguridad: así el log nombra la ruta sin copiar ningún trozo de la URL recibida. Si ninguna
 * coincide, devuelve {@code null} y la línea usa una etiqueta fija.
 */
public final class PlantillasRutas implements Function<HttpServletRequest, String> {

    private final RequestMappingHandlerMapping rutas;

    public PlantillasRutas(RequestMappingHandlerMapping rutas) {
        this.rutas = Objects.requireNonNull(rutas, "rutas");
    }

    @Override
    public String apply(HttpServletRequest peticion) {
        try {
            if (!ServletRequestPathUtils.hasParsedRequestPath(peticion)) {
                ServletRequestPathUtils.parseAndCache(peticion);
            }
            RequestMappingInfo mejor = null;
            for (RequestMappingInfo declarada : rutas.getHandlerMethods().keySet()) {
                RequestMappingInfo coincide = declarada.getMatchingCondition(peticion);
                if (coincide != null && (mejor == null || coincide.compareTo(mejor, peticion) < 0)) {
                    mejor = coincide;
                }
            }
            return mejor == null || mejor.getPatternValues().isEmpty()
                    ? null : mejor.getPatternValues().iterator().next();
        } catch (RuntimeException e) {
            return null; // El registro nunca debe fallar por esto: se usa la etiqueta fija.
        }
    }
}
