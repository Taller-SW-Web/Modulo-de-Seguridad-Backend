package com.marketplace.auth_service.usuario.adapter.in.web;

import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.dto.SolicitudRegistro;
import com.marketplace.auth_service.usuario.application.port.in.RegistroClientePort;
import com.marketplace.auth_service.usuario.domain.model.CanalOrigen;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST {@code /api/v1/auth/registro} (SPEC-01, FEAT-01-T1). Público. 201 con la cuenta
 * {@code PENDIENTE_VERIFICACION} y sin tokens; 400 {@code VALIDACION} por campo; 422
 * {@code POLITICA_INCUMPLIDA} con cada regla (longitud incluida, por eso aquí no se mide);
 * 409 {@code CORREO_NO_DISPONIBLE} con el texto neutro del contrato.
 */
@RestController
public class RegistroController {

    /** Cuerpo del registro; nunca se registra. */
    public record RegistroRequest(String correo, String contrasena, String nombres, String apellidos,
                                  String celular, Boolean aceptaTerminos, String canalOrigen) {
        @Override
        public String toString() {
            return "RegistroRequest[REDACTADO]";
        }
    }

    public record RegistroResponse(String id, String estado) { }

    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern CELULAR = Pattern.compile("^\\+51[0-9]{9}$");

    private final RegistroClientePort registro;

    public RegistroController(RegistroClientePort registro) {
        this.registro = registro;
    }

    @PostMapping(path = "/api/v1/auth/registro", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> registrar(@RequestBody(required = false) RegistroRequest cuerpo,
                                       HttpServletRequest peticion) {
        var cuerpoLeido = cuerpo == null ? new RegistroRequest(null, null, null, null, null, null, null) : cuerpo;
        List<Map<String, String>> errores = validar(cuerpoLeido);
        if (!errores.isEmpty()) {
            ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                    "Datos de entrada inválidos", null);
            problema.setProperty("errores", errores);
            return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
        }
        String canal = cuerpoLeido.canalOrigen() == null ? CanalOrigen.WEB.name() : cuerpoLeido.canalOrigen();
        var solicitud = new SolicitudRegistro(cuerpoLeido.correo(), cuerpoLeido.nombres(), cuerpoLeido.apellidos(),
                cuerpoLeido.celular(), canal, cuerpoLeido.contrasena());
        return switch (registro.registrar(solicitud, ContextoPeticiones.desde(peticion))) {
            case ResultadoRegistro.Creada creada -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(new RegistroResponse(creada.id().toString(), "PENDIENTE_VERIFICACION"));
            case ResultadoRegistro.Rechazo rechazo -> Problemas.respuesta(HttpStatus.CONFLICT, "CORREO_NO_DISPONIBLE",
                    "No se pudo completar el registro",
                    "Si el correo es válido, recibirás un mensaje con los pasos a seguir.");
            case ResultadoRegistro.PoliticaIncumplida incumplida -> ResponseEntity.unprocessableEntity()
                    .contentType(Problemas.PROBLEM_JSON).body(politicaIncumplida(incumplida));
        };
    }

    private static List<Map<String, String>> validar(RegistroRequest c) {
        List<Map<String, String>> errores = new ArrayList<>();
        if (vacio(c.correo())) {
            errores.add(error("correo", "Es obligatorio."));
        } else if (c.correo().strip().length() > 254 || !CORREO.matcher(c.correo().strip()).matches()) {
            errores.add(error("correo", "No es un correo válido."));
        }
        if (c.contrasena() == null || c.contrasena().isEmpty()) {
            errores.add(error("contrasena", "Es obligatoria."));
        }
        texto(errores, "nombres", c.nombres());
        texto(errores, "apellidos", c.apellidos());
        if (vacio(c.celular())) {
            errores.add(error("celular", "Es obligatorio."));
        } else if (!CELULAR.matcher(c.celular()).matches()) {
            errores.add(error("celular", "Debe ser +51 seguido de 9 dígitos."));
        }
        if (!Boolean.TRUE.equals(c.aceptaTerminos())) {
            errores.add(error("aceptaTerminos", "Debes aceptar los términos y el tratamiento de datos personales."));
        }
        if (c.canalOrigen() != null && Arrays.stream(CanalOrigen.values())
                .noneMatch(canal -> canal.name().equals(c.canalOrigen()))) {
            errores.add(error("canalOrigen", "No es un canal permitido."));
        }
        return errores;
    }

    private static void texto(List<Map<String, String>> errores, String campo, String valor) {
        if (vacio(valor)) {
            errores.add(error(campo, "Es obligatorio."));
        } else if (valor.strip().length() > 100) {
            errores.add(error(campo, "Admite como máximo 100 caracteres."));
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static Map<String, String> error(String campo, String mensaje) {
        return Map.of("campo", campo, "mensaje", mensaje);
    }

    /** Misma forma que el 422 de {@code /password/cambiar}: una entrada por regla, con su esperado si lo hay. */
    private static ProblemDetail politicaIncumplida(ResultadoRegistro.PoliticaIncumplida incumplida) {
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
