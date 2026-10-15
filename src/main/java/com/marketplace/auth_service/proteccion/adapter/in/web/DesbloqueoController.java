package com.marketplace.auth_service.proteccion.adapter.in.web;
import com.marketplace.auth_service.proteccion.application.dto.PruebaEnlace;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.adapter.web.Problemas;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class DesbloqueoController {
    private final ProteccionPort proteccion;
    public DesbloqueoController(ProteccionPort proteccion) { this.proteccion=proteccion; }
    @PostMapping(path="/api/v1/auth/desbloquear",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> desbloquear(@RequestBody(required=false) DesbloquearRequest body,HttpServletRequest request) {
        if (body==null || body.token()==null || body.token().isBlank())
            return Problemas.respuesta(HttpStatus.BAD_REQUEST,"VALIDACION","Datos de entrada inválidos","El token es obligatorio.");
        return switch(proteccion.desbloquear(new PruebaEnlace(body.token()),ContextoPeticiones.desde(request))) {
            case DESBLOQUEADA -> ResponseEntity.noContent().build();
            case INVALIDA -> Problemas.respuesta(HttpStatus.UNAUTHORIZED,"TOKEN_DESBLOQUEO_INVALIDO",
                    "Enlace de desbloqueo no válido",null);
            case EXPIRADA -> Problemas.respuesta(HttpStatus.GONE,"TOKEN_DESBLOQUEO_EXPIRADO",
                    "El enlace de desbloqueo expiró",null);
        };
    }
}
