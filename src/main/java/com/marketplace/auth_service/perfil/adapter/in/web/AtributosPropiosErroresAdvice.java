package com.marketplace.auth_service.perfil.adapter.in.web;

import com.marketplace.auth_service.perfil.domain.model.AtributoInvalidoException;
import com.marketplace.auth_service.perfil.domain.model.CuentaPerfilNoEncontradaException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores de {@link AtributosPropiosController}. Los datos inválidos responden 400
 * {@code VALIDACION} con {@code errores[]} por campo (catálogo de errores, FRONT-00 §2);
 * nunca se devuelve el valor recibido.
 */
@RestControllerAdvice(assignableTypes = AtributosPropiosController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class AtributosPropiosErroresAdvice {

    private static final Logger log = LoggerFactory.getLogger(AtributosPropiosErroresAdvice.class);
    private static final String TITULO_VALIDACION = "Datos de entrada inválidos";

    @ExceptionHandler(EdicionNoPermitidaException.class)
    ResponseEntity<ProblemDetail> noPermitida(EdicionNoPermitidaException e) {
        return Problemas.respuesta(HttpStatus.FORBIDDEN, "SCOPE_INSUFICIENTE", "Permisos insuficientes", null);
    }

    @ExceptionHandler(AtributoInvalidoException.class)
    ResponseEntity<ProblemDetail> invalido(AtributoInvalidoException e) {
        return validacion(e.campo(), e.motivo());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> cuerpoIlegible(HttpMessageNotReadableException e) {
        return validacion("cuerpo", "debe ser un objeto JSON válido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> idInvalido(MethodArgumentTypeMismatchException e) {
        return validacion("id", "identificador inválido");
    }

    @ExceptionHandler(CuentaPerfilNoEncontradaException.class)
    ResponseEntity<ProblemDetail> cuentaInexistente(CuentaPerfilNoEncontradaException e) {
        return Problemas.respuesta(HttpStatus.UNAUTHORIZED, "TOKEN_INVALIDO", "Token ausente o inválido", null);
    }

    @ExceptionHandler(DocumentoNoLegibleException.class)
    ResponseEntity<ProblemDetail> documentoNoLegible(DocumentoNoLegibleException e) {
        log.error("Perfil con documento no legible al editar: {}", e.getMessage());
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE", "Servicio no disponible", null);
    }

    private static ResponseEntity<ProblemDetail> validacion(String campo, String mensaje) {
        ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION", TITULO_VALIDACION, null);
        problema.setProperty("errores", List.of(Map.of("campo", campo, "mensaje", mensaje)));
        return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
    }
}
