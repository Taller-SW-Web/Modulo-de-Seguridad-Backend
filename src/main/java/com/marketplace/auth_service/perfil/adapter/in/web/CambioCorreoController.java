package com.marketplace.auth_service.perfil.adapter.in.web;

import com.marketplace.auth_service.perfil.application.dto.SolicitudCambioCorreo;
import com.marketplace.auth_service.perfil.application.port.in.CambioCorreoPort;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/usuarios/{id}/correo} (SPEC-16, FEAT-04-T3). Solo el titular, con su contraseña
 * actual. 202 enlace enviado; 401 {@code CREDENCIALES_INVALIDAS}; 403 si no es el titular; 409
 * {@code CORREO_NO_DISPONIBLE} con el mensaje neutro del contrato; 400 si falta o es inválido un campo.
 */
@RestController
public class CambioCorreoController {

    /** Cuerpo del cambio; nunca se registra. */
    public record CambioCorreoRequest(
            @NotBlank @Email @Size(max = 254) String nuevoCorreo,
            @NotBlank @Size(max = 1024) String contrasenaActual) {
        @Override
        public String toString() {
            return "CambioCorreoRequest[REDACTADO]";
        }
    }

    private final CambioCorreoPort cambio;

    public CambioCorreoController(CambioCorreoPort cambio) {
        this.cambio = cambio;
    }

    @PostMapping(path = "/api/v1/usuarios/{id}/correo", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> solicitar(@AuthenticationPrincipal ActorUsuario actor, @PathVariable UUID id,
                                       @Valid @RequestBody CambioCorreoRequest cuerpo, HttpServletRequest peticion) {
        var resultado = cambio.solicitar(actor, id,
                new SolicitudCambioCorreo(cuerpo.nuevoCorreo(), cuerpo.contrasenaActual()),
                ContextoPeticiones.desde(peticion));
        return switch (resultado) {
            case ENVIADA -> ResponseEntity.accepted().build();
            case CONTRASENA_INCORRECTA -> Problemas.respuesta(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
                    "Credenciales inválidas", "La contraseña actual no es correcta.");
            case CORREO_NO_DISPONIBLE -> Problemas.respuesta(HttpStatus.CONFLICT, "CORREO_NO_DISPONIBLE",
                    "No se pudo usar ese correo", null);
        };
    }
}
