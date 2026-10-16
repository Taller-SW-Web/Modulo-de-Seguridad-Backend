package com.marketplace.auth_service.credencial.adapter.in.web;

import com.marketplace.auth_service.credencial.application.dto.ResultadoCambioContrasena;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.CambioContrasenaPort;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST {@code /api/v1/password/cambiar} (SPEC-07, EP-03-T005). Requiere acceso de persona; el
 * titular es siempre el {@code sub}. 204 cambiada; 401 {@code CREDENCIALES_INVALIDAS} si la actual
 * no coincide; 422 {@code POLITICA_INCUMPLIDA} con cada regla; 400 si falta un campo.
 */
@RestController
public class CambioContrasenaController {

    /** Cuerpo del cambio; nunca se registra. */
    public record CambioContrasenaRequest(String contrasenaActual, String nuevaContrasena) {
        @Override
        public String toString() {
            return "CambioContrasenaRequest[REDACTADO]";
        }
    }

    private final CambioContrasenaPort cambio;

    public CambioContrasenaController(CambioContrasenaPort cambio) {
        this.cambio = cambio;
    }

    @PostMapping(path = "/api/v1/password/cambiar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cambiar(@AuthenticationPrincipal ActorUsuario actor,
                                     @RequestBody(required = false) CambioContrasenaRequest cuerpo,
                                     HttpServletRequest peticion) {
        List<Map<String, String>> faltan = new ArrayList<>();
        if (cuerpo == null || cuerpo.contrasenaActual() == null || cuerpo.contrasenaActual().isEmpty()) {
            faltan.add(Map.of("campo", "contrasenaActual", "mensaje", "Escribe tu contraseña actual."));
        }
        if (cuerpo == null || cuerpo.nuevaContrasena() == null || cuerpo.nuevaContrasena().isEmpty()) {
            faltan.add(Map.of("campo", "nuevaContrasena", "mensaje", "Escribe la nueva contraseña."));
        }
        if (!faltan.isEmpty()) {
            ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                    "Datos de entrada inválidos", null);
            problema.setProperty("errores", faltan);
            return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
        }
        var resultado = cambio.cambiar(actor.sub(), actor.jti(), new SecretoEfimero(cuerpo.contrasenaActual()),
                new SecretoEfimero(cuerpo.nuevaContrasena()), ContextoPeticiones.desde(peticion));
        return switch (resultado) {
            case ResultadoCambioContrasena.Simple simple -> switch (simple) {
                case CAMBIADA -> ResponseEntity.noContent().build();
                case ACTUAL_INCORRECTA -> Problemas.respuesta(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
                        "Credenciales inválidas", "La contraseña actual no es correcta.");
            };
            case ResultadoCambioContrasena.PoliticaIncumplida incumplida -> ResponseEntity.unprocessableEntity()
                    .contentType(Problemas.PROBLEM_JSON).body(PoliticaPasswordWebMapper.aProblema(incumplida.validacion()));
        };
    }
}
