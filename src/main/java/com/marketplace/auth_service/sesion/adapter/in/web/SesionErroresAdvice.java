package com.marketplace.auth_service.sesion.adapter.in.web;

import com.marketplace.auth_service.sesion.domain.exception.DatoObligatorioException;
import com.marketplace.auth_service.sesion.domain.exception.DependenciaNoDisponibleException;
import com.marketplace.auth_service.sesion.domain.exception.SecretoInvalidoException;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/** Errores de los controladores de sesión según el contrato (RFC 7807). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
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

    /** Falta un dato del cuerpo: {@code 400 VALIDACION} con campo y mensaje. */
    @ExceptionHandler(DatoObligatorioException.class)
    ResponseEntity<ProblemDetail> datoObligatorio(DatoObligatorioException e) {
        ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Datos de entrada inválidos", null);
        problema.setProperty("errores", List.of(Map.of("campo", e.campo(), "mensaje", e.getMessage())));
        return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
    }

    /** El cuerpo no es JSON válido: {@code 400 VALIDACION}, nunca HTML. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> cuerpoIlegible(HttpMessageNotReadableException e) {
        log.info("sesion: cuerpo ilegible: {}", e.getMessage());
        ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Datos de entrada inválidos", null);
        problema.setProperty("errores",
                List.of(Map.of("campo", "cuerpo", "mensaje", "El cuerpo debe ser JSON válido.")));
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
