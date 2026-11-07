package com.marketplace.auth_service.usuario.adapter.in.web;

import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAdministracionCuenta;
import com.marketplace.auth_service.usuario.application.port.in.BajaReactivacionPort;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code DELETE /api/v1/usuarios/{id}} y {@code POST /api/v1/usuarios/{id}/reactivar} (SPEC-04, FEAT-02).
 * Solo token de persona con {@code usuario.desactivar} / {@code usuario.reactivar}. 204; 400 id
 * malformado; 403 {@code SCOPE_INSUFICIENTE}; 404 {@code NO_ENCONTRADO}; 422
 * {@code ADMINISTRADOR_PROTEGIDO}.
 */
@RestController
public class BajaReactivacionController {

    private static final Logger log = LoggerFactory.getLogger(BajaReactivacionController.class);

    private final BajaReactivacionPort operaciones;

    public BajaReactivacionController(BajaReactivacionPort operaciones) {
        this.operaciones = operaciones;
    }

    @DeleteMapping("/api/v1/usuarios/{id}")
    public ResponseEntity<?> darDeBaja(@AuthenticationPrincipal ActorUsuario actor, @PathVariable("id") String id,
                                       HttpServletRequest peticion) {
        return respuesta(operaciones.darDeBaja(actor, id, ContextoPeticiones.desde(peticion)));
    }

    @PostMapping("/api/v1/usuarios/{id}/reactivar")
    public ResponseEntity<?> reactivar(@AuthenticationPrincipal ActorUsuario actor, @PathVariable("id") String id,
                                       HttpServletRequest peticion) {
        return respuesta(operaciones.reactivar(actor, id, ContextoPeticiones.desde(peticion)));
    }

    private static ResponseEntity<?> respuesta(ResultadoAdministracionCuenta resultado) {
        return switch (resultado) {
            case HECHO -> ResponseEntity.noContent().build();
            case VALIDACION -> Problemas.respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", "Datos de entrada inválidos",
                    "El identificador de usuario no es válido.");
            case NO_ENCONTRADO -> Problemas.respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Usuario no encontrado", null);
            case ADMINISTRADOR_PROTEGIDO -> Problemas.respuesta(HttpStatus.UNPROCESSABLE_ENTITY, "ADMINISTRADOR_PROTEGIDO",
                    "Operación no permitida sobre este administrador", null);
            case SIN_PERMISO -> Problemas.respuesta(HttpStatus.FORBIDDEN, "SCOPE_INSUFICIENTE", "Permisos insuficientes", null);
        };
    }

    /** Auditoría crítica u otra dependencia caída: la baja se revirtió (RF-04.4). */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> noDisponible(RuntimeException e) {
        log.warn("Baja o reactivación rechazada con 503: la operación se revirtió", e);
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE", "Servicio no disponible", null);
    }
}
