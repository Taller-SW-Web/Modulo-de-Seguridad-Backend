package com.marketplace.auth_service.shared.adapter.web;

import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.TreeSet;

/**
 * Anota quién llama en cuanto el token se validó, dentro de la cadena de seguridad. Hace falta
 * porque el contexto de seguridad se limpia antes de que el registro escriba la línea.
 *
 * <p>Solo guarda datos que no identifican a una persona fuera del sistema: el {@code client_id}
 * del módulo, o los 8 primeros caracteres del {@code sub} con sus roles. Nunca el token ni el correo.
 */
public final class CapturaLlamante extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String llamante = describir(SecurityContextHolder.getContext().getAuthentication());
        if (llamante != null) {
            peticion.setAttribute(RegistroPeticiones.ATRIBUTO_LLAMANTE, llamante);
        }
        cadena.doFilter(peticion, respuesta);
    }

    static String describir(Authentication autenticacion) {
        if (autenticacion == null) {
            return null;
        }
        Object principal = autenticacion.getPrincipal();
        if (principal instanceof ActorServicio servicio) {
            return LineaPeticion.limpiar(servicio.clientId());
        }
        if (principal instanceof ActorUsuario usuario) {
            String roles = usuario.roles() == null || usuario.roles().isEmpty()
                    ? "sin rol" : String.join(",", new TreeSet<>(usuario.roles()));
            return "usuario " + usuario.sub().toString().substring(0, 8) + " (" + roles + ")";
        }
        return null;
    }
}
