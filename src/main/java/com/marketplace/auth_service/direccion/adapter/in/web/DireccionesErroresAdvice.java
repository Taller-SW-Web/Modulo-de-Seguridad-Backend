package com.marketplace.auth_service.direccion.adapter.in.web;

import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.direccion.domain.model.DireccionNoEncontradaException;
import com.marketplace.auth_service.direccion.domain.model.DireccionNoPermitidaException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = DireccionesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class DireccionesErroresAdvice {
    @ExceptionHandler(DireccionNoPermitidaException.class)
    ResponseEntity<ProblemDetail> noPermitida() {
        return Problemas.respuesta(HttpStatus.FORBIDDEN,"SCOPE_INSUFICIENTE","Permisos insuficientes",null);
    }
    @ExceptionHandler(DireccionNoEncontradaException.class)
    ResponseEntity<ProblemDetail> noEncontrada() {
        return Problemas.respuesta(HttpStatus.NOT_FOUND,"NO_ENCONTRADO","Recurso no encontrado",null);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> cuerpoInvalido(Exception e) {
        return Problemas.respuesta(HttpStatus.BAD_REQUEST,"VALIDACION","Datos de entrada inválidos",null);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validacion(MethodArgumentNotValidException e) {
        ProblemDetail p=Problemas.problema(HttpStatus.BAD_REQUEST,"VALIDACION","Datos de entrada inválidos",null);
        p.setProperty("errores", e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("campo",error.getField(),"mensaje","valor inválido")).distinct().toList());
        return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(p);
    }
}
