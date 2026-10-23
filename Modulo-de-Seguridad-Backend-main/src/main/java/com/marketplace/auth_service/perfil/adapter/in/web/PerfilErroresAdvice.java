package com.marketplace.auth_service.perfil.adapter.in.web;

import com.marketplace.auth_service.perfil.domain.model.CuentaPerfilNoEncontradaException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de {@link PerfilController}. Un acceso firmado cuya cuenta ya no existe
 * no identifica a nadie: 401 {@code TOKEN_INVALIDO}. Un documento que no se puede
 * descifrar es un fallo del servidor y nunca se responde sin él como si no existiera.
 */
@RestControllerAdvice(assignableTypes = PerfilController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class PerfilErroresAdvice {

    private static final Logger log = LoggerFactory.getLogger(PerfilErroresAdvice.class);

    @ExceptionHandler(CuentaPerfilNoEncontradaException.class)
    ResponseEntity<ProblemDetail> cuentaInexistente(CuentaPerfilNoEncontradaException e) {
        return Problemas.respuesta(HttpStatus.UNAUTHORIZED, "TOKEN_INVALIDO", "Token ausente o inválido", null);
    }

    @ExceptionHandler(DocumentoNoLegibleException.class)
    ResponseEntity<ProblemDetail> documentoNoLegible(DocumentoNoLegibleException e) {
        log.error("Perfil con documento no legible: {}", e.getMessage());
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE", "Servicio no disponible", null);
    }
}
