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
@RestControllerAdvice
public class SesionErroresAdvice {

    private static final Logger log = LoggerFactory.getLogger(SesionErroresAdvice.class);

    /** El cuerpo no trae el refresco: {@code 400 VALIDACION} con campo y mensaje. */
    @ExceptionHandler(SecretoInvalidoException.class)
    ResponseEntity<ProblemDetail> cuerpoInvalido(SecretoInvalidoException e) {
        ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Datos de entrada inválidos", null);
        problema.setProperty("errores", List.of(Map.of("campo", "refreshToken", "mensaje", e.getMessage())));
        return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
    }

    /** Capacidad dueña aún sin puerto: 503 sin éxito parcial ni secreto. */
    @ExceptionHandler(DependenciaNoDisponibleException.class)
    ResponseEntity<ProblemDetail> dependenciaNoDisponible(DependenciaNoDisponibleException e) {
        log.warn("sesion: {}", e.getMessage());
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE",
                "Servicio no disponible", "Inténtalo nuevamente.");
    }
}
