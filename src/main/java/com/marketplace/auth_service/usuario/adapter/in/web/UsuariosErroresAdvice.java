package com.marketplace.auth_service.usuario.adapter.in.web;

import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.usuario.domain.exception.ConsultaInvalidaException;
import com.marketplace.auth_service.usuario.domain.exception.CuentaNoEncontradaException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de {@link UsuariosController} con los códigos del catálogo:
 * 400 {@code VALIDACION} con el campo y 404 {@code NO_ENCONTRADO}.
 */
@RestControllerAdvice(assignableTypes = UsuariosController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class UsuariosErroresAdvice {

    static final String TITULO_VALIDACION = "Datos de entrada inválidos";

    @ExceptionHandler(ConsultaInvalidaException.class)
    ResponseEntity<ProblemDetail> consultaInvalida(ConsultaInvalidaException e) {
        ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION", TITULO_VALIDACION, null);
        problema.setProperty("errores", List.of(Map.of("campo", e.campo(), "mensaje", e.getMessage())));
        return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
    }

    @ExceptionHandler(CuentaNoEncontradaException.class)
    ResponseEntity<ProblemDetail> noEncontrada(CuentaNoEncontradaException e) {
        return Problemas.respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Usuario no encontrado", null);
    }
}
