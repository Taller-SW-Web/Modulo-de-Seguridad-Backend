package com.marketplace.auth_service.sesion.adapter.in.web;

import com.marketplace.auth_service.sesion.domain.exception.DependenciaNoDisponibleException;
import com.marketplace.auth_service.sesion.domain.exception.SecretoInvalidoException;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/** Errores de los controladores de sesión según el contrato (RFC 7807). */
@RestControllerAdvice(assignableTypes = {RefrescoController.class, CierreController.class, LoginController.class})
@org.springframework.core.annotation.Order(-1)
public class SesionErroresAdvice {

    private static final Logger log = LoggerFactory.getLogger(SesionErroresAdvice.class);

    /** No incluir la excepción de Jackson: puede contener el cuerpo con el secreto. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> jsonInvalido() {
        return Problemas.respuesta(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Datos de entrada inválidos", "El cuerpo JSON no es válido.");
    }

    /** El cuerpo no trae el refresco: {@code 400 VALIDACION} con campo y mensaje. */
    @ExceptionHandler(SecretoInvalidoException.class)
    ResponseEntity<ProblemDetail> cuerpoInvalido(SecretoInvalidoException e) {
        ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Datos de entrada inválidos", null);
        problema.setProperty("errores", List.of(Map.of("campo", "refreshToken", "mensaje", e.getMessage())));
        return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
    }

    /**
     * Base o capacidad dueña caída a mitad del caso de uso: 503 sin éxito parcial, sin
     * secretos ni SQL en la respuesta (la transacción ya se revirtió). Nunca un 401 que
     * confunda con credenciales o token.
     */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<ProblemDetail> baseNoDisponible(org.springframework.dao.DataAccessException e) {
        log.error("sesion: acceso a datos fallido ({})", e.getClass().getSimpleName());
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE",
                "Servicio no disponible", "Inténtalo nuevamente.");
    }

    /** Capacidad dueña aún sin puerto: 503 sin éxito parcial ni secreto. */
    @ExceptionHandler(DependenciaNoDisponibleException.class)
    ResponseEntity<ProblemDetail> dependenciaNoDisponible(DependenciaNoDisponibleException e) {
        log.warn("sesion: {}", e.getMessage());
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE",
                "Servicio no disponible", "Inténtalo nuevamente.");
    }
}
