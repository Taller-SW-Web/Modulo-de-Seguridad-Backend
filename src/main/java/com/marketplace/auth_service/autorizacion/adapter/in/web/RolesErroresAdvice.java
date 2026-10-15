package com.marketplace.auth_service.autorizacion.adapter.in.web;

import com.marketplace.auth_service.autorizacion.domain.exception.AdministradorProtegidoException;
import com.marketplace.auth_service.autorizacion.domain.exception.GestionRolesNoDisponibleException;
import com.marketplace.auth_service.autorizacion.domain.exception.RolInvalidoException;
import com.marketplace.auth_service.autorizacion.domain.exception.UsuarioNoEncontradoException;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

/**
 * Errores de {@link RolesController} con los códigos del catálogo
 * (SPEC-11 en {@code catalogo-errores.md}). El 403 {@code SCOPE_INSUFICIENTE}
 * lo produce {@code AutorizacionErroresAdvice}, común a todos los controladores.
 */
@RestControllerAdvice(assignableTypes = RolesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class RolesErroresAdvice {

    private static final Logger log = LoggerFactory.getLogger(RolesErroresAdvice.class);

    static final String TITULO_VALIDACION = "Datos de entrada inválidos";
    static final String NO_PERTENECE = "No pertenece al catálogo cerrado de roles.";
    static final String OBLIGATORIO = "Es obligatorio.";
    /** Mensaje de ESC-11.5. */
    static final String UNICO_ADMINISTRADOR = "No se puede eliminar el único administrador del sistema";
    static final String PROPIO_ADMINISTRADOR = "No puedes quitarte tu propio rol de administrador del sistema";

    @ExceptionHandler(RolInvalidoException.class)
    ResponseEntity<ProblemDetail> rolInvalido(RolInvalidoException e) {
        return validacion("rol", e.codigo() == null ? OBLIGATORIO : NO_PERTENECE);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> cuerpoIlegible(HttpMessageNotReadableException e) {
        return validacion("rol", "El cuerpo debe ser JSON con el campo rol.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return validacion(e.getName(), "No es un identificador válido.");
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    ResponseEntity<ProblemDetail> noEncontrado(UsuarioNoEncontradoException e) {
        return Problemas.respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Usuario no encontrado", null);
    }

    @ExceptionHandler(AdministradorProtegidoException.class)
    ResponseEntity<ProblemDetail> administradorProtegido(AdministradorProtegidoException e) {
        String detalle = e.motivo() == AdministradorProtegidoException.Motivo.ULTIMO_ADMINISTRADOR
                ? UNICO_ADMINISTRADOR : PROPIO_ADMINISTRADOR;
        return Problemas.respuesta(HttpStatus.UNPROCESSABLE_ENTITY, "ADMINISTRADOR_PROTEGIDO",
                "Operación no permitida sobre este administrador", detalle);
    }

    @ExceptionHandler(GestionRolesNoDisponibleException.class)
    ResponseEntity<ProblemDetail> noDisponible(GestionRolesNoDisponibleException e) {
        log.warn("Cambio de rol rechazado con 503: {}", e.getMessage());
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE", "Servicio no disponible", null);
    }

    private static ResponseEntity<ProblemDetail> validacion(String campo, String mensaje) {
        ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION", TITULO_VALIDACION, null);
        problema.setProperty("errores", List.of(Map.of("campo", campo, "mensaje", mensaje)));
        return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
    }
}
