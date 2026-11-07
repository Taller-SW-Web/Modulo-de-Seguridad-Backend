package com.marketplace.auth_service.proteccion.adapter.in.web;

import com.marketplace.auth_service.proteccion.application.port.in.BloqueoManualPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.adapter.web.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class BloqueoManualController {
    private final BloqueoManualPort operaciones;
    public BloqueoManualController(BloqueoManualPort operaciones) { this.operaciones=operaciones; }
    @PostMapping("/api/v1/usuarios/{id}/bloquear")
    public ResponseEntity<?> bloquear(@AuthenticationPrincipal ActorUsuario actor,@PathVariable("id") String id,
            @RequestBody(required=false) Map<String,Object> cuerpo,HttpServletRequest peticion) {
        String motivo=cuerpo!=null && cuerpo.get("motivo") instanceof String s ? s : null;
        return respuesta(operaciones.ejecutar(actor,id,true,motivo,ContextoPeticiones.desde(peticion)));
    }
    @PostMapping("/api/v1/usuarios/{id}/desbloquear")
    public ResponseEntity<?> desbloquear(@AuthenticationPrincipal ActorUsuario actor,@PathVariable("id") String id,HttpServletRequest peticion) {
        return respuesta(operaciones.ejecutar(actor,id,false,null,ContextoPeticiones.desde(peticion)));
    }
    private ResponseEntity<?> respuesta(String codigo) {
        if(codigo==null) return ResponseEntity.noContent().build();
        HttpStatus estado=switch(codigo) {
            case "VALIDACION" -> HttpStatus.BAD_REQUEST;
            case "NO_ENCONTRADO" -> HttpStatus.NOT_FOUND;
            case "ADMINISTRADOR_PROTEGIDO" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.FORBIDDEN;
        };
        return Problemas.respuesta(estado,codigo,"No se pudo completar la operación",null);
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<?> invalido() { return respuesta("VALIDACION"); }
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<?> noDisponible() { return Problemas.respuesta(HttpStatus.SERVICE_UNAVAILABLE,"NO_DISPONIBLE","Servicio no disponible",null); }
}
