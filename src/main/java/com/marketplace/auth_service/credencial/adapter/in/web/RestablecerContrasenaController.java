package com.marketplace.auth_service.credencial.adapter.in.web;

import com.marketplace.auth_service.credencial.application.dto.ResultadoRestablecimiento;
import com.marketplace.auth_service.credencial.application.dto.SecretoEfimero;
import com.marketplace.auth_service.credencial.application.port.in.RestablecerContrasenaPort;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint público SPEC-08: nunca refleja ni registra el token o la contraseña. */
@RestController
public class RestablecerContrasenaController {
    public record RestablecerRequest(String token, String nuevaContrasena) {
        @Override public String toString() { return "RestablecerRequest[REDACTADO]"; }
    }
    private final RestablecerContrasenaPort restablecimiento;
    public RestablecerContrasenaController(RestablecerContrasenaPort restablecimiento) {
        this.restablecimiento=restablecimiento;
    }

    @PostMapping(path="/api/v1/password/restablecer", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> restablecer(@RequestBody(required=false) RestablecerRequest cuerpo,
                                         HttpServletRequest peticion) {
        List<Map<String,String>> errores=new ArrayList<>();
        if (cuerpo==null || cuerpo.token()==null || cuerpo.token().isBlank() || cuerpo.token().length()>256)
            errores.add(Map.of("campo","token","mensaje","El enlace no es válido."));
        if (cuerpo==null || cuerpo.nuevaContrasena()==null || cuerpo.nuevaContrasena().isEmpty()
                || cuerpo.nuevaContrasena().length()>1024)
            errores.add(Map.of("campo","nuevaContrasena","mensaje","Escribe una contraseña válida."));
        if (!errores.isEmpty()) {
            ProblemDetail problema=Problemas.problema(HttpStatus.BAD_REQUEST,"VALIDACION","Datos de entrada inválidos",null);
            problema.setProperty("errores",errores);
            return ResponseEntity.badRequest().contentType(Problemas.PROBLEM_JSON).body(problema);
        }
        var resultado=restablecimiento.restablecer(new SecretoEfimero(cuerpo.token()),
                new SecretoEfimero(cuerpo.nuevaContrasena()),ContextoPeticiones.desde(peticion));
        return switch (resultado) {
            case ResultadoRestablecimiento.Simple simple -> switch (simple) {
                case RESTABLECIDA -> ResponseEntity.noContent().build();
                case TOKEN_INVALIDO -> Problemas.respuesta(HttpStatus.UNAUTHORIZED,"TOKEN_RECUPERACION_INVALIDO",
                        "Enlace inválido","Solicita un nuevo enlace de recuperación.");
                case TOKEN_EXPIRADO -> Problemas.respuesta(HttpStatus.GONE,"TOKEN_RECUPERACION_EXPIRADO",
                        "Enlace expirado","Solicita un nuevo enlace de recuperación.");
            };
            case ResultadoRestablecimiento.PoliticaIncumplida politica -> ResponseEntity.unprocessableEntity()
                    .contentType(Problemas.PROBLEM_JSON)
                    .body(PoliticaPasswordWebMapper.aProblema(politica.validacion()));
        };
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> noDisponible() {
        return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE,"NO_DISPONIBLE",
                "Servicio no disponible","Inténtalo nuevamente.");
    }
}
