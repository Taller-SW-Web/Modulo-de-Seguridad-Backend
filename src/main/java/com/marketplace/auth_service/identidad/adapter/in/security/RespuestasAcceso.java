package com.marketplace.auth_service.identidad.adapter.in.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Errores del filtro de acceso con el formato del contrato (RFC 7807), nunca
 * la página HTML ni el JSON por defecto de Spring.
 *
 * <ul>
 *   <li>Sin token, token inválido o vencido: {@code 401 TOKEN_INVALIDO} con
 *       {@code WWW-Authenticate: Bearer}. Es el único 401 que habilita a la SPA
 *       a intentar una renovación (ENTORNO-Y-REACT §2).</li>
 *   <li>Token de servicio en operación de persona: {@code 403 TOKEN_NO_APLICABLE}.</li>
 *   <li>Autenticado sin permiso para la ruta: {@code 403 SCOPE_INSUFICIENTE}.</li>
 *   <li>Validación imposible por falta de clave: {@code 503 NO_DISPONIBLE}.</li>
 * </ul>
 */
public class RespuestasAcceso implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper json;

    public RespuestasAcceso(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException e)
            throws IOException {
        if (e instanceof TokenNoAplicableAuthenticationException) {
            escribir(respuesta, Problemas.problema(HttpStatus.FORBIDDEN, "TOKEN_NO_APLICABLE",
                    "Este endpoint requiere un token de usuario", null));
        } else if (e instanceof AuthenticationServiceException) {
            escribir(respuesta, Problemas.problema(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE",
                    "Servicio no disponible", null));
        } else {
            respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            escribir(respuesta, Problemas.problema(HttpStatus.UNAUTHORIZED, "TOKEN_INVALIDO",
                    "Token ausente o inválido", null));
        }
    }

    @Override
    public void handle(HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException e)
            throws IOException {
        escribir(respuesta, Problemas.problema(HttpStatus.FORBIDDEN, "SCOPE_INSUFICIENTE",
                "Permisos insuficientes", null));
    }

    private void escribir(HttpServletResponse respuesta, ProblemDetail problema) throws IOException {
        respuesta.setStatus(problema.getStatus());
        respuesta.setContentType(Problemas.PROBLEM_JSON.toString());
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(), problema);
    }
}
