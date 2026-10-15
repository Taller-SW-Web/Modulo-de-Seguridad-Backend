package com.marketplace.auth_service.proteccion.adapter.in.web;
import com.marketplace.auth_service.proteccion.domain.exception.*;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=DesbloqueoController.class)
@Order(-1)
public class ProteccionErroresAdvice {
    @ExceptionHandler({ProteccionNoDisponibleException.class,HuellaNoDisponibleException.class})
    public ResponseEntity<ProblemDetail> dependencia() {
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE,"NO_DISPONIBLE","Servicio no disponible",null);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> formato() {
        return Problemas.respuesta(HttpStatus.BAD_REQUEST,"VALIDACION","Datos de entrada inválidos","El cuerpo JSON no es válido.");
    }
}
