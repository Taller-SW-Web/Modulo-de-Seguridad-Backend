package com.marketplace.auth_service.identidad.adapter.in.web;

import com.marketplace.auth_service.identidad.adapter.in.web.dto.IntrospeccionRequest;
import com.marketplace.auth_service.identidad.adapter.in.web.dto.IntrospeccionResponse;
import com.marketplace.auth_service.identidad.adapter.in.web.dto.TokenServicioResponse;
import com.marketplace.auth_service.identidad.application.dto.ResultadoIntrospeccion;
import com.marketplace.auth_service.identidad.application.dto.ResultadoTokenServicio;
import com.marketplace.auth_service.identidad.application.dto.SolicitudTokenServicio;
import com.marketplace.auth_service.identidad.application.port.in.IntrospeccionPort;
import com.marketplace.auth_service.identidad.application.port.in.TokenServicioPort;
import com.marketplace.auth_service.identidad.domain.exception.FirmaNoDisponibleException;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.shared.domain.ActorServicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Integración de módulos consumidores (SPEC-17).
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/token}: pública; el módulo se autentica con su
 *       {@code client_id} y {@code client_secret} en el formulario (ESC-17.3).</li>
 *   <li>{@code POST /api/v1/auth/introspeccion}: exige token de servicio con
 *       {@code tokens:introspeccion}; la regla de la ruta ya lo comprobó.</li>
 * </ul>
 * Ninguna respuesta lleva el token en logs; las de token no se cachean.
 */
@RestController
public class IntegracionController {

    static final String TITULO_CLIENTE_INVALIDO = "Credenciales de cliente inválidas";
    static final String TITULO_VALIDACION = "Datos de entrada inválidos";

    private static final Logger log = LoggerFactory.getLogger(IntegracionController.class);

    private final TokenServicioPort tokens;
    private final IntrospeccionPort introspeccion;

    public IntegracionController(TokenServicioPort tokens, IntrospeccionPort introspeccion) {
        this.tokens = tokens;
        this.introspeccion = introspeccion;
    }

    @PostMapping(path = "/api/v1/auth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> token(@RequestParam(name = "grant_type", required = false) String grantType,
                                   @RequestParam(name = "client_id", required = false) String clientId,
                                   @RequestParam(name = "client_secret", required = false) String clientSecret,
                                   @RequestParam(name = "scope", required = false) String scope) {
        ResultadoTokenServicio resultado = tokens.emitir(new SolicitudTokenServicio(grantType, clientId, clientSecret, scope));
        return switch (resultado) {
            case ResultadoTokenServicio.Emitido emitido -> ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(new TokenServicioResponse(emitido.token().token(), "Bearer", emitido.token().expiresIn(),
                            emitido.scopes()));
            case ResultadoTokenServicio.ClienteInvalido invalido -> Problemas.respuesta(HttpStatus.UNAUTHORIZED,
                    "CLIENTE_INVALIDO", TITULO_CLIENTE_INVALIDO, "No se pudo emitir el token de servicio.");
            case ResultadoTokenServicio.Invalida invalida -> validacion(invalida.campo(), invalida.mensaje());
        };
    }

    @PostMapping(path = "/api/v1/auth/introspeccion", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> introspeccionar(@AuthenticationPrincipal ActorServicio actor,
                                             @RequestBody(required = false) IntrospeccionRequest cuerpo) {
        if (cuerpo == null || cuerpo.token() == null || cuerpo.token().isBlank()) {
            return validacion("token", "Es obligatorio.");
        }
        IntrospeccionResponse respuesta = switch (introspeccion.introspeccionar(actor, cuerpo.token())) {
            case ResultadoIntrospeccion.Activa a -> new IntrospeccionResponse(true, null, a.sub().toString(),
                    a.email(), a.roles(), a.permisos(), "acceso", a.expiraEn().getEpochSecond());
            case ResultadoIntrospeccion.Inactiva i -> IntrospeccionResponse.inactiva(i.motivo().name());
        };
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(respuesta);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> cuerpoIlegible() {
        return validacion("cuerpo", "No es un JSON válido.");
    }

    @ExceptionHandler(FirmaNoDisponibleException.class)
    ResponseEntity<ProblemDetail> noDisponible(FirmaNoDisponibleException e) {
        log.warn("identidad: emisión de tokens de servicio no disponible: {}", e.getMessage());
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE", "Servicio no disponible", null);
    }

    private static ResponseEntity<ProblemDetail> validacion(String campo, String mensaje) {
        ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION", TITULO_VALIDACION, null);
        problema.setProperty("errores", List.of(Map.of("campo", campo, "mensaje", mensaje)));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(Problemas.PROBLEM_JSON).body(problema);
    }
}
