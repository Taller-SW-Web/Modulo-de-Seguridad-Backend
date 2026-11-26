package com.marketplace.auth_service.usuario.adapter.in.web;

import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.Rol;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAltaPersonal;
import com.marketplace.auth_service.usuario.application.dto.SolicitudAltaPersonal;
import com.marketplace.auth_service.usuario.application.port.in.AltaPersonalPort;
import com.marketplace.auth_service.usuario.application.port.in.ConsultaUsuariosPort;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v2/usuarios} (SPEC-03 RF-03.1, OpenAPI v2 {@code crearUsuarioV2}; FEAT-13-T1). Solo
 * token de persona de un {@code ADMIN_SISTEMA} con {@code usuario.crear}. 201 con la cuenta
 * {@code PENDIENTE_VERIFICACION}; 400 {@code VALIDACION} por campo, también si llega un campo que el
 * esquema estricto no admite (contraseña, varios roles); 403 {@code SCOPE_INSUFICIENTE} antes de mirar el
 * cuerpo; 409 {@code CORREO_NO_DISPONIBLE}; 503 {@code NO_DISPONIBLE} si no se pudo encolar el enlace.
 */
@RestController
public class AltaPersonalController {

    private static final Logger log = LoggerFactory.getLogger(AltaPersonalController.class);
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    /** {@code CrearUsuarioRequest} tiene {@code additionalProperties: false}. */
    private static final Set<String> CAMPOS = Set.of("correo", "nombres", "apellidos", "rol");

    private final AltaPersonalPort alta;
    private final ConsultaUsuariosPort consulta;

    public AltaPersonalController(AltaPersonalPort alta, ConsultaUsuariosPort consulta) {
        this.alta = alta;
        this.consulta = consulta;
    }

    @PostMapping(path = "/api/v2/usuarios", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> crear(@AuthenticationPrincipal ActorUsuario actor,
                                   @RequestBody(required = false) Map<String, Object> cuerpo,
                                   HttpServletRequest peticion) {
        var contexto = ContextoPeticiones.desde(peticion);
        if (!alta.autorizar(actor, contexto)) return sinPermiso();
        Map<String, Object> leido = cuerpo == null ? Map.of() : cuerpo;
        List<Map<String, String>> errores = validar(leido);
        if (!errores.isEmpty()) {
            ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                    "Datos de entrada inválidos", null);
            problema.setProperty("errores", errores);
            return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
        }
        String rol = (String) leido.get("rol");
        var solicitud = new SolicitudAltaPersonal((String) leido.get("correo"), (String) leido.get("nombres"),
                (String) leido.get("apellidos"), rol == null ? null : Rol.valueOf(rol));
        return switch (alta.crear(actor, solicitud, contexto)) {
            case ResultadoAltaPersonal.Creada creada -> ResponseEntity
                    .created(URI.create("/api/v1/usuarios/" + creada.id()))
                    .body(UsuariosWebMapper.aRespuesta(consulta.obtener(actor, creada.id().toString())));
            case ResultadoAltaPersonal.Rechazo.SIN_PERMISO -> sinPermiso();
            case ResultadoAltaPersonal.Rechazo.CORREO_NO_DISPONIBLE -> Problemas.respuesta(HttpStatus.CONFLICT,
                    "CORREO_NO_DISPONIBLE", "No se pudo usar ese correo", null);
        };
    }

    /** La cola de correo no está disponible: el alta se revirtió y puede reintentarse (catálogo, NO_DISPONIBLE). */
    @ExceptionHandler(VerificacionNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> noDisponible(VerificacionNoDisponibleException e) {
        log.warn("Alta administrativa rechazada con 503: no se pudo encolar el enlace de activación", e);
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE", "Servicio no disponible",
                "No pudimos crear la cuenta. Inténtalo de nuevo en unos minutos.");
    }

    private static ResponseEntity<ProblemDetail> sinPermiso() {
        return Problemas.respuesta(HttpStatus.FORBIDDEN, "SCOPE_INSUFICIENTE", "Permisos insuficientes", null);
    }

    private static List<Map<String, String>> validar(Map<String, Object> c) {
        List<Map<String, String>> errores = new ArrayList<>();
        c.keySet().stream().filter(campo -> !CAMPOS.contains(campo)).sorted()
                .forEach(campo -> errores.add(error(campo, "No se admite este campo.")));
        Object correo = c.get("correo");
        if (!(correo instanceof String texto) || texto.isBlank()) {
            errores.add(error("correo", "Es obligatorio."));
        } else if (texto.strip().length() > 254 || !CORREO.matcher(texto.strip()).matches()) {
            errores.add(error("correo", "No es un correo válido."));
        }
        texto(errores, "nombres", c.get("nombres"));
        texto(errores, "apellidos", c.get("apellidos"));
        Object rol = c.get("rol");
        if (rol != null && !(rol instanceof String codigo && rolDePersonal(codigo))) {
            errores.add(error("rol", "Debe ser VENDEDOR, ADMIN_VENTAS, GESTOR_DESPACHO, GESTOR_COMERCIAL o ADMIN_SISTEMA."));
        }
        return errores;
    }

    /** Los cinco roles del contrato v2; {@code CLIENTE} usa el registro público o asistido. */
    private static boolean rolDePersonal(String codigo) {
        return Rol.buscar(codigo).filter(r -> r != Rol.CLIENTE).isPresent();
    }

    private static void texto(List<Map<String, String>> errores, String campo, Object valor) {
        if (!(valor instanceof String texto) || texto.isBlank()) {
            errores.add(error(campo, "Es obligatorio."));
        } else if (texto.strip().length() > 100) {
            errores.add(error(campo, "Admite como máximo 100 caracteres."));
        }
    }

    private static Map<String, String> error(String campo, String mensaje) {
        return Map.of("campo", campo, "mensaje", mensaje);
    }
}
