package com.marketplace.auth_service.shared.adapter.web;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * Arma el {@link ContextoPeticion} de una petición HTTP. La IP es la que
 * resuelve el servidor: detrás del proxy de la nube, la configuración de
 * cabeceras reenviadas del entorno decide cuál es. Nunca copia cabeceras de
 * autorización ni el cuerpo.
 */
public final class ContextoPeticiones {

    private ContextoPeticiones() {
    }

    public static ContextoPeticion desde(HttpServletRequest peticion) {
        return new ContextoPeticion(peticion.getRemoteAddr(), peticion.getHeader("User-Agent"), UUID.randomUUID());
    }
}
