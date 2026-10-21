package com.marketplace.auth_service.identidad.adapter.in.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.auth_service.identidad.application.dto.DenegacionAcceso;
import com.marketplace.auth_service.identidad.application.port.out.AuditoriaAccesoPort;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

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
 *
 * <p>Cada 403 se audita como {@code ACCESO_DENEGADO}: {@code SCOPE_INSUFICIENTE}
 * (SPEC-17 ESC-17.8) y {@code TOKEN_NO_APLICABLE} (ESC-17.4). Si la auditoría
 * falla, se anota en el log y el 403 sale igual.
 */
public class RespuestasAcceso implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RespuestasAcceso.class);

    private final ObjectMapper json;
    private final AuditoriaAccesoPort auditoria;
    private final Clock reloj;

    /** Sin auditoría: solo para contextos parciales que no la cargan. */
    public RespuestasAcceso(ObjectMapper json) {
        this(json, denegacion -> { }, Clock.systemUTC());
    }

    public RespuestasAcceso(ObjectMapper json, AuditoriaAccesoPort auditoria, Clock reloj) {
        this.json = json;
        this.auditoria = Objects.requireNonNull(auditoria, "auditoria");
        this.reloj = Objects.requireNonNull(reloj, "reloj");
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException e)
            throws IOException {
        if (e instanceof TokenNoAplicableAuthenticationException noAplicable) {
            // ESC-17.4: un módulo intentó actuar como persona. El client_id y el jti ya están verificados.
            registrar(peticion, denegacion(peticion, DenegacionAcceso.TOKEN_NO_APLICABLE, DenegacionAcceso.MODULO,
                    noAplicable.clientId() == null ? "desconocido" : noAplicable.clientId(),
                    uuidONulo(noAplicable.jti())));
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
        auditar(peticion);
        escribir(respuesta, Problemas.problema(HttpStatus.FORBIDDEN, "SCOPE_INSUFICIENTE",
                "Permisos insuficientes", null));
    }

    private void auditar(HttpServletRequest peticion) {
        Authentication quien = SecurityContextHolder.getContext().getAuthentication();
        if (quien instanceof ServicioAutenticado servicio) {
            registrar(peticion, denegacion(peticion, DenegacionAcceso.SCOPE_INSUFICIENTE, DenegacionAcceso.MODULO,
                    servicio.getPrincipal().clientId(), servicio.getPrincipal().jti()));
        } else if (quien instanceof ActorAutenticado persona) {
            registrar(peticion, denegacion(peticion, DenegacionAcceso.SCOPE_INSUFICIENTE, DenegacionAcceso.USUARIO,
                    persona.getPrincipal().sub().toString(), persona.getPrincipal().jti()));
        }
    }

    private void registrar(HttpServletRequest peticion, DenegacionAcceso denegacion) {
        try {
            auditoria.accesoDenegado(denegacion);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar ACCESO_DENEGADO en {}: {}", peticion.getRequestURI(),
                    e.getClass().getSimpleName());
        }
    }

    private DenegacionAcceso denegacion(HttpServletRequest peticion, String motivo, String tipo, String id, UUID jti) {
        return new DenegacionAcceso(motivo, tipo, id, jti, peticion.getMethod(), peticion.getRequestURI(),
                ContextoPeticiones.desde(peticion), reloj.instant());
    }

    private static UUID uuidONulo(String valor) {
        try {
            return valor == null ? null : UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void escribir(HttpServletResponse respuesta, ProblemDetail problema) throws IOException {
        respuesta.setStatus(problema.getStatus());
        respuesta.setContentType(Problemas.PROBLEM_JSON.toString());
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(), problema);
    }
}
