package com.marketplace.auth_service.autorizacion.adapter.in.web;

import com.marketplace.auth_service.autorizacion.domain.exception.AccesoDenegadoException;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las denegaciones de {@code AutorizacionPort} en cualquier controlador:
 * {@code 403 SCOPE_INSUFICIENTE}, sin decir qué permiso faltaba (catálogo de
 * errores). El permiso y el actor quedan en el log del servidor; la auditoría
 * {@code ACCESO_DENEGADO} se conectará cuando exista {@code AuditoriaPort} (Christian).
 */
@RestControllerAdvice
public class AutorizacionErroresAdvice {

    private static final Logger log = LoggerFactory.getLogger(AutorizacionErroresAdvice.class);

    @ExceptionHandler(AccesoDenegadoException.class)
    ResponseEntity<ProblemDetail> accesoDenegado(AccesoDenegadoException e) {
        log.info("acceso denegado: actor={} permiso={}", e.actor(), e.permiso().codigo());
        return Problemas.respuesta(HttpStatus.FORBIDDEN, "SCOPE_INSUFICIENTE", "Permisos insuficientes", null);
    }
}
