package com.marketplace.auth_service.sesion.adapter.in.web;

import com.marketplace.auth_service.sesion.adapter.in.web.dto.IniciarSesionRequest;
import com.marketplace.auth_service.sesion.application.dto.ResultadoLogin;
import com.marketplace.auth_service.sesion.application.port.in.LoginPort;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST {@code /api/v1/auth/login} (SPEC-05).
 *
 * <p>Es público. Éxito con sesión, desafío MFA sin tokens, mismo
 * {@code 401 CREDENCIALES_INVALIDAS} para errónea/inexistente/bloqueada/
 * inactiva/pendiente, y {@code 403 PASSWORD_CADUCADA} al completar
 * autenticación de gestión caducada. Sin tokens ni secretos en errores.
 */
@RestController
public class LoginController {

    private final LoginPort login;

    public LoginController(LoginPort login) {
        this.login = login;
    }

    @PostMapping(path = "/api/v1/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> ingresar(@RequestBody(required = false) IniciarSesionRequest cuerpo,
                                      HttpServletRequest peticion) {
        ResultadoLogin resultado =
                login.ingresar(LoginWebMapper.aComando(cuerpo), ContextoPeticiones.desde(peticion));
        return switch (resultado) {
            case ResultadoLogin.Exito exito ->
                    ResponseEntity.ok(SesionWebMapper.aRespuesta(exito.sesion()));
            case ResultadoLogin.Desafio desafio ->
                    ResponseEntity.ok(LoginWebMapper.aRespuesta(desafio.desafio()));
            case ResultadoLogin.Rechazado ignored -> Problemas.respuesta(HttpStatus.UNAUTHORIZED,
                    "CREDENCIALES_INVALIDAS", "Credenciales inválidas",
                    "El correo o la contraseña no son correctos.");
            case ResultadoLogin.Caducada ignored -> Problemas.respuesta(HttpStatus.FORBIDDEN,
                    "PASSWORD_CADUCADA", "La contraseña caducó", null);
        };
    }
}
