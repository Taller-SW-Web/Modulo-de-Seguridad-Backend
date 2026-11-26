package com.marketplace.auth_service.usuario.adapter.in.web;

import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.usuario.application.dto.ResultadoActivacion;
import com.marketplace.auth_service.usuario.application.port.in.ActivacionCuentaPort;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/auth/activar-cuenta} (SPEC-03 RF-03.5, OpenAPI v1 {@code activarCuenta}; FEAT-13-T1).
 * Público. 204 cuenta activa; 400 {@code VALIDACION} si falta el token o la contraseña o no se aceptan los
 * términos; 410 {@code ENLACE_EXPIRADO} o {@code ENLACE_YA_USADO} (también para un token desconocido);
 * 422 {@code POLITICA_INCUMPLIDA} con cada regla, y el enlace sigue sirviendo.
 */
@RestController
public class ActivacionCuentaController {

    /** Cuerpo de la activación; nunca se registra. */
    public record ActivarCuentaRequest(String token, String contrasena, Boolean aceptaTerminos) {
        @Override
        public String toString() {
            return "ActivarCuentaRequest[REDACTADO]";
        }
    }

    private final ActivacionCuentaPort activacion;

    public ActivacionCuentaController(ActivacionCuentaPort activacion) {
        this.activacion = activacion;
    }

    @PostMapping(path = "/api/v1/auth/activar-cuenta", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> activar(@RequestBody(required = false) ActivarCuentaRequest cuerpo,
                                     HttpServletRequest peticion) {
        var leido = cuerpo == null ? new ActivarCuentaRequest(null, null, null) : cuerpo;
        List<Map<String, String>> errores = new ArrayList<>();
        if (leido.token() == null || leido.token().isBlank()) errores.add(error("token", "Es obligatorio."));
        // Solo falta si no llega: una contraseña vacía la rechaza la política con 422, como en el registro.
        if (leido.contrasena() == null) errores.add(error("contrasena", "Es obligatoria."));
        if (!Boolean.TRUE.equals(leido.aceptaTerminos())) {
            errores.add(error("aceptaTerminos", "Debes aceptar los términos y el tratamiento de datos personales."));
        }
        if (!errores.isEmpty()) {
            ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                    "Datos de entrada inválidos", null);
            problema.setProperty("errores", errores);
            return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
        }
        return switch (activacion.activar(leido.token(), leido.contrasena(), ContextoPeticiones.desde(peticion))) {
            case ResultadoActivacion.Estado.ACTIVADA -> ResponseEntity.noContent().build();
            case ResultadoActivacion.Estado.EXPIRADA -> Problemas.respuesta(HttpStatus.GONE, "ENLACE_EXPIRADO",
                    "El enlace venció", null);
            case ResultadoActivacion.Estado.YA_USADA, ResultadoActivacion.Estado.INVALIDA ->
                    Problemas.respuesta(HttpStatus.GONE, "ENLACE_YA_USADO", "El enlace ya se usó", null);
            case ResultadoActivacion.PoliticaIncumplida incumplida -> ResponseEntity.unprocessableEntity()
                    .contentType(Problemas.PROBLEM_JSON).body(politicaIncumplida(incumplida));
        };
    }

    private static Map<String, String> error(String campo, String mensaje) {
        return Map.of("campo", campo, "mensaje", mensaje);
    }

    /** Misma forma que el 422 del registro y de {@code /password/cambiar}. */
    private static ProblemDetail politicaIncumplida(ResultadoActivacion.PoliticaIncumplida incumplida) {
        var problema = Problemas.problema(HttpStatus.UNPROCESSABLE_ENTITY, "POLITICA_INCUMPLIDA",
                "La contraseña no cumple la política", null);
        problema.setProperty("errores", incumplida.reglas().stream().map(regla -> {
            var campos = new LinkedHashMap<String, Object>();
            campos.put("regla", regla.regla());
            if (regla.esperado() != null) campos.put("esperado", regla.esperado());
            return campos;
        }).toList());
        return problema;
    }
}
