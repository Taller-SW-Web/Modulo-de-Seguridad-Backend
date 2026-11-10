package com.marketplace.auth_service.identidad.adapter.in.web;

import com.marketplace.auth_service.identidad.application.dto.ClienteTecnico;
import com.marketplace.auth_service.identidad.application.dto.ResultadoGestionCliente;
import com.marketplace.auth_service.identidad.application.dto.ScopeAsignado;
import com.marketplace.auth_service.identidad.application.dto.SecretoEntregado;
import com.marketplace.auth_service.identidad.application.port.in.ClientesTecnicosPort;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Configuración de clientes técnicos (SPEC-19, FEAT-14), en {@code /api/v2}.
 * Solo token de persona: un token de servicio recibe {@code 403 TOKEN_NO_APLICABLE}
 * en la cadena de seguridad, antes de llegar aquí. Los cuerpos son cerrados:
 * una clave desconocida es {@code 400 VALIDACION}.
 */
@RestController
public class ClientesTecnicosController {

    static final String BASE = "/api/v2/clientes-servicio";
    private static final Set<String> CAMPOS_ALTA = Set.of("clientId", "nombre", "scopes");
    private static final Map<String, String> MENSAJES = Map.of(
            "clientId", "De 3 a 50 caracteres: minúsculas, números y guiones; empieza con letra o número.",
            "nombre", "Entre 1 y 100 caracteres.",
            "scopes", "Solo scopes del catálogo, sin repetir.",
            "scope", "Debe ser un scope del catálogo.");

    private final ClientesTecnicosPort clientes;

    public ClientesTecnicosController(ClientesTecnicosPort clientes) {
        this.clientes = clientes;
    }

    @GetMapping(BASE)
    public ResponseEntity<?> listar(@AuthenticationPrincipal ActorUsuario actor, HttpServletRequest peticion) {
        return respuesta(clientes.listar(actor, ContextoPeticiones.desde(peticion)),
                lista -> ResponseEntity.ok(lista.stream().map(ClienteResponse::de).toList()));
    }

    @GetMapping("/api/v2/scopes")
    public ResponseEntity<?> catalogo(@AuthenticationPrincipal ActorUsuario actor, HttpServletRequest peticion) {
        return respuesta(clientes.catalogo(actor, ContextoPeticiones.desde(peticion)), ResponseEntity::ok);
    }

    @PostMapping(BASE)
    public ResponseEntity<?> crear(@AuthenticationPrincipal ActorUsuario actor,
                                   @RequestBody(required = false) Map<String, Object> cuerpo,
                                   HttpServletRequest peticion) {
        if (cuerpo == null || !CAMPOS_ALTA.containsAll(cuerpo.keySet())) {
            return validacion(null);
        }
        if (!(cuerpo.get("clientId") instanceof String clientId)) {
            return validacion("clientId");
        }
        if (!(cuerpo.get("nombre") instanceof String nombre)) {
            return validacion("nombre");
        }
        List<String> scopes = new ArrayList<>();
        Object pedidos = cuerpo.get("scopes");
        if (pedidos != null) {
            if (!(pedidos instanceof List<?> lista)) {
                return validacion("scopes");
            }
            for (Object scope : lista) {
                if (!(scope instanceof String texto)) {
                    return validacion("scopes");
                }
                scopes.add(texto);
            }
        }
        return respuesta(clientes.crear(actor, clientId, nombre, scopes, ContextoPeticiones.desde(peticion)),
                entregado -> secreto(HttpStatus.CREATED, entregado));
    }

    @PostMapping(BASE + "/{clientId}/secreto")
    public ResponseEntity<?> rotar(@AuthenticationPrincipal ActorUsuario actor,
                                   @PathVariable("clientId") String clientId, HttpServletRequest peticion) {
        return respuesta(clientes.rotarSecreto(actor, clientId, ContextoPeticiones.desde(peticion)),
                entregado -> secreto(HttpStatus.OK, entregado));
    }

    @PostMapping(BASE + "/{clientId}/desactivar")
    public ResponseEntity<?> desactivar(@AuthenticationPrincipal ActorUsuario actor,
                                        @PathVariable("clientId") String clientId, HttpServletRequest peticion) {
        return sinContenido(clientes.cambiarEstado(actor, clientId, false, ContextoPeticiones.desde(peticion)));
    }

    @PostMapping(BASE + "/{clientId}/reactivar")
    public ResponseEntity<?> reactivar(@AuthenticationPrincipal ActorUsuario actor,
                                       @PathVariable("clientId") String clientId, HttpServletRequest peticion) {
        return sinContenido(clientes.cambiarEstado(actor, clientId, true, ContextoPeticiones.desde(peticion)));
    }

    @PostMapping(BASE + "/{clientId}/scopes")
    public ResponseEntity<?> conceder(@AuthenticationPrincipal ActorUsuario actor,
                                      @PathVariable("clientId") String clientId,
                                      @RequestBody(required = false) Map<String, Object> cuerpo,
                                      HttpServletRequest peticion) {
        if (cuerpo == null || !cuerpo.keySet().equals(Set.of("scope"))
                || !(cuerpo.get("scope") instanceof String scope)) {
            return validacion("scope");
        }
        return sinContenido(clientes.conceder(actor, clientId, scope, ContextoPeticiones.desde(peticion)));
    }

    @DeleteMapping(BASE + "/{clientId}/scopes/{scope}")
    public ResponseEntity<?> revocar(@AuthenticationPrincipal ActorUsuario actor,
                                     @PathVariable("clientId") String clientId,
                                     @PathVariable("scope") String scope, HttpServletRequest peticion) {
        return sinContenido(clientes.revocar(actor, clientId, scope, ContextoPeticiones.desde(peticion)));
    }

    private static ResponseEntity<?> secreto(HttpStatus estado, SecretoEntregado entregado) {
        return ResponseEntity.status(estado).cacheControl(CacheControl.noStore())
                .body(ClienteConSecretoResponse.de(entregado));
    }

    private static ResponseEntity<?> sinContenido(ResultadoGestionCliente<Void> resultado) {
        return respuesta(resultado, nada -> ResponseEntity.noContent().build());
    }

    private static <T> ResponseEntity<?> respuesta(ResultadoGestionCliente<T> resultado,
                                                   Function<T, ResponseEntity<?>> correcto) {
        return switch (resultado) {
            case ResultadoGestionCliente.Correcto<T> ok -> correcto.apply(ok.valor());
            case ResultadoGestionCliente.Rechazado<T> rechazo -> error(rechazo.codigo(), rechazo.campo());
        };
    }

    private static ResponseEntity<?> validacion(String campo) {
        return error(ResultadoGestionCliente.VALIDACION, campo);
    }

    private static ResponseEntity<ProblemDetail> error(String codigo, String campo) {
        HttpStatus estado = switch (codigo) {
            case ResultadoGestionCliente.VALIDACION -> HttpStatus.BAD_REQUEST;
            case ResultadoGestionCliente.NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case ResultadoGestionCliente.CLIENTE_SERVICIO_EXISTENTE -> HttpStatus.CONFLICT;
            default -> HttpStatus.FORBIDDEN;
        };
        String titulo = switch (codigo) {
            case ResultadoGestionCliente.VALIDACION -> "Datos de entrada inválidos";
            case ResultadoGestionCliente.NO_ENCONTRADO -> "Recurso no encontrado";
            case ResultadoGestionCliente.CLIENTE_SERVICIO_EXISTENTE -> "Ya existe un cliente con ese identificador";
            default -> "Permisos insuficientes";
        };
        ProblemDetail problema = Problemas.problema(estado, codigo, titulo, null);
        if (campo != null) {
            problema.setProperty("errores", List.of(Map.of("campo", campo, "mensaje", MENSAJES.get(campo))));
        }
        return ResponseEntity.status(estado).contentType(Problemas.PROBLEM_JSON).body(problema);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> cuerpoInvalido() {
        return validacion(null);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<?> noDisponible() {
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE", "Servicio no disponible",
                "Inténtalo nuevamente.");
    }

    public record ClienteResponse(String clientId, String nombre, boolean activo, List<ScopeAsignado> scopes) {
        static ClienteResponse de(ClienteTecnico cliente) {
            return new ClienteResponse(cliente.clientId(), cliente.nombre(), cliente.activo(), cliente.scopes());
        }
    }

    public record ClienteConSecretoResponse(String clientId, String nombre, boolean activo,
                                            List<ScopeAsignado> scopes, String clientSecret) {
        static ClienteConSecretoResponse de(SecretoEntregado entregado) {
            ClienteTecnico c = entregado.cliente();
            return new ClienteConSecretoResponse(c.clientId(), c.nombre(), c.activo(), c.scopes(),
                    entregado.clientSecret());
        }

        @Override
        public String toString() {
            return "ClienteConSecretoResponse[clientId=" + clientId + ", clientSecret=REDACTADO]";
        }
    }
}
