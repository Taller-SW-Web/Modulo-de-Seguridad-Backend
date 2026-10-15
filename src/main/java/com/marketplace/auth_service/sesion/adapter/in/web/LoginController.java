package com.marketplace.auth_service.sesion.adapter.in.web;

import com.marketplace.auth_service.sesion.adapter.in.web.dto.DesafioMfaResponse;
import com.marketplace.auth_service.sesion.adapter.in.web.dto.LoginRequest;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.sesion.application.port.in.LoginPort;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import com.marketplace.auth_service.sesion.adapter.in.web.dto.VerificarOtpRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST {@code /api/v1/auth/login} (SPEC-05). Público. Responde {@code SesionIniciada} o
 * {@code DesafioMfa} con 200; cualquier rechazo de identidad es el mismo
 * {@code 401 CREDENCIALES_INVALIDAS} (también con cuerpo vacío, para no distinguir
 * casos), y la caducidad es {@code 403 PASSWORD_CADUCADA} al completar la autenticación.
 */
@RestController
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final LoginPort login;

    public LoginController(LoginPort login) {
        this.login = login;
    }

    @PostMapping(path = "/api/v1/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> iniciar(@RequestBody(required = false) LoginRequest cuerpo, HttpServletRequest peticion) {
        String correo = cuerpo == null ? null : cuerpo.correo();
        String contrasena = cuerpo == null ? null : cuerpo.contrasena();
        ResultadoLogin resultado = login.iniciar(correo, contrasena, ContextoPeticiones.desde(peticion));
        return switch (resultado) {
            case ResultadoLogin.Iniciada iniciada -> {
                log.info("sesión iniciada: usuario={}", iniciada.sesion().usuario().id());
                yield ResponseEntity.ok(SesionWebMapper.aRespuesta(iniciada.sesion()));
            }
            case ResultadoLogin.DesafioRequerido desafio -> ResponseEntity.ok(new DesafioMfaResponse(true,
                    desafio.desafio().challengeToken(), desafio.desafio().expiraEn(), desafio.desafio().canal()));
            default -> respuesta(resultado);
        };
    }

    /** POST {@code /api/v1/auth/otp/verificar} (SPEC-09, EP-04-T004): código correcto → SesionIniciada. */
    @PostMapping(path = "/api/v1/auth/otp/verificar", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> verificar(@RequestBody(required = false) VerificarOtpRequest cuerpo,
                                       HttpServletRequest peticion) {
        String token = cuerpo == null ? null : cuerpo.challengeToken();
        String codigo = cuerpo == null ? null : cuerpo.codigo();
        if (codigo == null || !codigo.matches("[0-9]{6}")) {
            ProblemDetail problema = Problemas.problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                    "Datos de entrada inválidos", null);
            problema.setProperty("errores", List.of(Map.of("campo", "codigo", "mensaje", "El código tiene 6 dígitos.")));
            return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
        }
        ResultadoLogin resultado = login.completarMfa(token, codigo, ContextoPeticiones.desde(peticion));
        if (resultado instanceof ResultadoLogin.Iniciada iniciada) {
            log.info("sesión iniciada con MFA: usuario={}", iniciada.sesion().usuario().id());
            return ResponseEntity.ok(SesionWebMapper.aRespuesta(iniciada.sesion()));
        }
        return respuesta(resultado);
    }

    private static ResponseEntity<?> respuesta(ResultadoLogin resultado) {
        return switch (resultado) {
            case ResultadoLogin.CodigoInvalido invalido -> {
                ProblemDetail problema = Problemas.problema(HttpStatus.UNAUTHORIZED, "CODIGO_INVALIDO",
                        "Código inválido", "El código no es correcto.");
                problema.setProperty("intentosRestantes", invalido.intentosRestantes());
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(Problemas.PROBLEM_JSON).body(problema);
            }
            case ResultadoLogin.Rechazo rechazo -> switch (rechazo) {
                case CREDENCIALES_INVALIDAS -> Problemas.respuesta(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS",
                        "Credenciales inválidas", "El correo o la contraseña no son correctos.");
                case CONTRASENA_CADUCADA -> Problemas.respuesta(HttpStatus.FORBIDDEN, "PASSWORD_CADUCADA",
                        "La contraseña caducó", "Restablece tu contraseña para continuar.");
                case DESAFIO_INVALIDO -> Problemas.respuesta(HttpStatus.UNAUTHORIZED, "TOKEN_INVALIDO",
                        "Token inválido", "Vuelve a iniciar sesión.");
                case OTP_INTENTOS_AGOTADOS -> Problemas.respuesta(HttpStatus.UNAUTHORIZED, "OTP_INTENTOS_AGOTADOS",
                        "Se agotaron los intentos", "Pide un código nuevo.");
                case OTP_EXPIRADO -> Problemas.respuesta(HttpStatus.GONE, "OTP_EXPIRADO",
                        "El código venció", "Pide un código nuevo.");
            };
            case ResultadoLogin.Iniciada i -> throw new IllegalStateException("éxito sin mapear");
            case ResultadoLogin.DesafioRequerido d -> throw new IllegalStateException("desafío sin mapear");
        };
    }
}
