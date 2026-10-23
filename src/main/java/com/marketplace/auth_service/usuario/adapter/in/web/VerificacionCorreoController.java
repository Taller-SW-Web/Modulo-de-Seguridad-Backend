package com.marketplace.auth_service.usuario.adapter.in.web;

import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.usuario.application.dto.ResultadoReenvio;
import com.marketplace.auth_service.usuario.application.port.in.VerificacionCorreoPort;
import com.marketplace.auth_service.usuario.domain.exception.CuotaNoDisponibleException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificación de correo (SPEC-02, FEAT-01-T3). Públicos.
 *
 * <ul>
 *   <li>{@code POST /auth/verificar-correo}: 204 verificado; 410 {@code ENLACE_EXPIRADO} o
 *       {@code ENLACE_YA_USADO} (también para un token desconocido); 400 si falta el token.</li>
 *   <li>{@code POST /auth/verificar-correo/reenviar}: 202 exista o no la cuenta; 429
 *       {@code DEMASIADAS_SOLICITUDES} con {@code Retry-After}; 400 si el correo no es válido.</li>
 * </ul>
 */
@RestController
public class VerificacionCorreoController {

    /** Cuerpo de la verificación; nunca se registra. */
    public record VerificarCorreoRequest(String token) {
        @Override
        public String toString() {
            return "VerificarCorreoRequest[REDACTADO]";
        }
    }

    public record ReenviarVerificacionRequest(String correo) {
        @Override
        public String toString() {
            return "ReenviarVerificacionRequest[REDACTADO]";
        }
    }

    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final VerificacionCorreoPort verificacion;

    public VerificacionCorreoController(VerificacionCorreoPort verificacion) {
        this.verificacion = verificacion;
    }

    @PostMapping(path = "/api/v1/auth/verificar-correo", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> verificar(@RequestBody(required = false) VerificarCorreoRequest cuerpo,
                                       HttpServletRequest peticion) {
        if (cuerpo == null || cuerpo.token() == null || cuerpo.token().isBlank()) {
            return validacion("token", "Es obligatorio.");
        }
        return switch (verificacion.verificar(cuerpo.token(), ContextoPeticiones.desde(peticion))) {
            case VERIFICADA -> ResponseEntity.noContent().build();
            case EXPIRADA -> Problemas.respuesta(HttpStatus.GONE, "ENLACE_EXPIRADO",
                    "El enlace de verificación expiró", null);
            case YA_USADA, INVALIDA -> Problemas.respuesta(HttpStatus.GONE, "ENLACE_YA_USADO",
                    "El enlace de verificación ya se usó", null);
        };
    }

    @PostMapping(path = "/api/v1/auth/verificar-correo/reenviar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reenviar(@RequestBody(required = false) ReenviarVerificacionRequest cuerpo,
                                      HttpServletRequest peticion) {
        String correo = cuerpo == null || cuerpo.correo() == null ? null : cuerpo.correo().strip();
        if (correo == null || correo.isEmpty()) {
            return validacion("correo", "Es obligatorio.");
        }
        if (correo.length() > 254 || !CORREO.matcher(correo).matches()) {
            return validacion("correo", "No es un correo válido.");
        }
        return switch (verificacion.reenviar(correo, ContextoPeticiones.desde(peticion))) {
            case ResultadoReenvio.Aceptado aceptado -> ResponseEntity.accepted().build();
            case ResultadoReenvio.Limitado limitado -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(limitado.reintentarEnSegundos()))
                    .contentType(Problemas.PROBLEM_JSON)
                    .body(Problemas.problema(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADAS_SOLICITUDES",
                            "Demasiadas solicitudes", "Espera antes de pedir otro enlace."));
        };
    }

    /** Sin la clave de la cuota el reenvío falla igual para cualquier correo (catálogo, NO_DISPONIBLE). */
    @ExceptionHandler(CuotaNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> cuotaNoDisponible() {
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE", "Servicio no disponible",
                "Inténtalo de nuevo en unos minutos.");
    }

    private static ResponseEntity<ProblemDetail> validacion(String campo, String mensaje) {
        ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Datos de entrada inválidos", null);
        problema.setProperty("errores", List.of(Map.of("campo", campo, "mensaje", mensaje)));
        return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
    }
}
