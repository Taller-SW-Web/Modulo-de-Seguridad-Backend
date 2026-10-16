package com.marketplace.auth_service.mfa.adapter.in.web;

import com.marketplace.auth_service.mfa.application.dto.CanalMfa;
import com.marketplace.auth_service.mfa.application.dto.ResultadoSolicitudOtp;
import com.marketplace.auth_service.mfa.application.port.in.OtpLoginPort;
import com.marketplace.auth_service.mfa.domain.exception.CriptografiaMfaNoDisponibleException;
import com.marketplace.auth_service.mfa.domain.exception.EntregaOtpNoDisponibleException;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST {@code /api/v1/auth/otp/solicitar} (SPEC-09, EP-04-T003). Público: lo autoriza el
 * {@code challengeToken}. Un desafío inválido es {@code 401 TOKEN_INVALIDO} sin revelar la
 * cuenta ni enviar nada; la cuota da {@code 429} con {@code Retry-After}.
 */
@RestController
public class OtpController {

    /** Cuerpo de la solicitud; el token nunca se registra. */
    public record SolicitarOtpRequest(String challengeToken, String canal) {
        @Override
        public String toString() {
            return "SolicitarOtpRequest[REDACTADO]";
        }
    }

    public record OtpEnviadoResponse(String canal, String destinoEnmascarado, int expiraEn, int desafioExpiraEn) { }

    private final OtpLoginPort otp;

    public OtpController(OtpLoginPort otp) {
        this.otp = otp;
    }

    @PostMapping(path = "/api/v1/auth/otp/solicitar", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> solicitar(@RequestBody(required = false) SolicitarOtpRequest cuerpo) {
        String token = cuerpo == null ? null : cuerpo.challengeToken();
        CanalMfa canal;
        try {
            canal = cuerpo == null || cuerpo.canal() == null ? CanalMfa.EMAIL : CanalMfa.valueOf(cuerpo.canal());
        } catch (IllegalArgumentException e) {
            return validacion("canal", "El canal debe ser EMAIL o SMS.");
        }
        return switch (otp.solicitar(token, canal)) {
            case ResultadoSolicitudOtp.Enviado enviado -> ResponseEntity.accepted().body(new OtpEnviadoResponse(
                    enviado.canal().name(), enviado.destinoEnmascarado(), enviado.expiraEn(), enviado.desafioExpiraEn()));
            case ResultadoSolicitudOtp.Limite limite -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(limite.reintentarEn()))
                    .contentType(Problemas.PROBLEM_JSON)
                    .body(Problemas.problema(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADAS_SOLICITUDES",
                            "Demasiadas solicitudes", "Espera antes de pedir otro código."));
            case ResultadoSolicitudOtp.Rechazo rechazo -> switch (rechazo) {
                case DESAFIO_INVALIDO -> Problemas.respuesta(HttpStatus.UNAUTHORIZED, "TOKEN_INVALIDO",
                        "Token ausente o inválido", "Vuelve a iniciar sesión.");
                case SIN_CELULAR -> validacion("canal", "La cuenta no tiene un celular registrado.");
            };
        };
    }

    @ExceptionHandler({EntregaOtpNoDisponibleException.class, CriptografiaMfaNoDisponibleException.class})
    ResponseEntity<ProblemDetail> noDisponible() {
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE, "NO_DISPONIBLE",
                "Servicio no disponible", "Inténtalo nuevamente.");
    }

    private static ResponseEntity<ProblemDetail> validacion(String campo, String mensaje) {
        ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                "Datos de entrada inválidos", null);
        problema.setProperty("errores", List.of(Map.of("campo", campo, "mensaje", mensaje)));
        return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
    }
}
