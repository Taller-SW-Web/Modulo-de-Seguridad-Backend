package com.marketplace.auth_service.mfa.adapter.in.web;

import com.marketplace.auth_service.mfa.application.dto.ResultadoCambioMfa;
import com.marketplace.auth_service.mfa.application.port.in.CambioMfaPort;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Mapas se validan contra el cuerpo cerrado de v2; nunca permiten elegir titular/destino. */
@RestController
@RequestMapping("/api/v2/auth/mfa/cambios")
public class CambioMfaController {
    private final CambioMfaPort cambios;
    public CambioMfaController(CambioMfaPort cambios) { this.cambios = cambios; }
    @PostMapping
    public ResponseEntity<?> solicitar(@AuthenticationPrincipal ActorUsuario actor,
            @RequestBody(required = false) Map<String, Object> cuerpo, HttpServletRequest peticion) {
        if (!valido(cuerpo, "accion")) return respuesta(ResultadoCambioMfa.error("VALIDACION"));
        return respuesta(cambios.solicitar(actor, (String)cuerpo.get("accion"), ContextoPeticiones.desde(peticion)));
    }
    @PostMapping("/reenviar")
    public ResponseEntity<?> reenviar(@AuthenticationPrincipal ActorUsuario actor,
            @RequestBody(required = false) Map<String, Object> cuerpo, HttpServletRequest peticion) {
        if (!valido(cuerpo, "mfaChangeToken")) return respuesta(ResultadoCambioMfa.error("VALIDACION"));
        return respuesta(cambios.reenviar(actor, (String)cuerpo.get("mfaChangeToken"), ContextoPeticiones.desde(peticion)));
    }
    @PostMapping("/confirmar")
    public ResponseEntity<?> confirmar(@AuthenticationPrincipal ActorUsuario actor,
            @RequestBody(required = false) Map<String, Object> cuerpo, HttpServletRequest peticion) {
        if (!valido(cuerpo, "mfaChangeToken", "codigo") || !((String)cuerpo.get("codigo")).matches("[0-9]{6}"))
            return respuesta(ResultadoCambioMfa.error("VALIDACION"));
        return respuesta(cambios.confirmar(actor, (String)cuerpo.get("mfaChangeToken"), (String)cuerpo.get("codigo"),
                ContextoPeticiones.desde(peticion)));
    }
    private static boolean valido(Map<String, Object> cuerpo, String... claves) {
        return cuerpo != null && cuerpo.keySet().equals(java.util.Set.of(claves))
                && java.util.Arrays.stream(claves).allMatch(c -> cuerpo.get(c) instanceof String s && !s.isBlank());
    }
    private static ResponseEntity<?> respuesta(ResultadoCambioMfa resultado) {
        if (resultado instanceof ResultadoCambioMfa.Enviado e) return ResponseEntity.accepted().body(
                new CambioEnviadoResponse(e.mfaChangeToken(), e.canal(), e.destinoEnmascarado(), e.expiraEn(), e.cambioExpiraEn()));
        if (resultado == ResultadoCambioMfa.Confirmado.EXITO) return ResponseEntity.noContent().build();
        var error = (ResultadoCambioMfa.Error)resultado;
        HttpStatus estado = switch(error.code()) {
            case "VALIDACION" -> HttpStatus.BAD_REQUEST;
            case "MFA_OBLIGATORIO" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "DEMASIADAS_SOLICITUDES" -> HttpStatus.TOO_MANY_REQUESTS;
            case "OTP_EXPIRADO" -> HttpStatus.GONE;
            default -> HttpStatus.UNAUTHORIZED;
        };
        var problema = Problemas.problema(estado, error.code(), "No se pudo completar el cambio", null);
        if (error.intentosRestantes() != null) problema.setProperty("intentosRestantes", error.intentosRestantes());
        var respuesta = ResponseEntity.status(estado).contentType(Problemas.PROBLEM_JSON);
        if (error.retryAfter() != null) respuesta.header("Retry-After", error.retryAfter().toString());
        return respuesta.body(problema);
    }
    public record CambioEnviadoResponse(String mfaChangeToken, String canal, String destinoEnmascarado,
            int expiraEn, int cambioExpiraEn) {
        @Override public String toString() { return "CambioEnviadoResponse[token=REDACTADO]"; }
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<?> cuerpoInvalido() { return respuesta(ResultadoCambioMfa.error("VALIDACION")); }
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<?> noDisponible(RuntimeException fallo) {
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE", "Servicio no disponible", "Inténtalo nuevamente.");
    }
}
