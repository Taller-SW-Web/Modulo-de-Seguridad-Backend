package com.marketplace.auth_service.autorizacion.adapter.in.web;

import com.marketplace.auth_service.autorizacion.adapter.in.web.dto.AsignarRolRequest;
import com.marketplace.auth_service.autorizacion.adapter.in.web.dto.UsuarioRolesResponse;
import com.marketplace.auth_service.autorizacion.application.port.in.GestionRolesPort;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Gestión de roles de SPEC-11 (operaciones {@code asignarRol} y {@code revocarRol} del contrato).
 *
 * <p>El actor sale del acceso que validó el filtro (TEC-IDENTIDAD-T002), nunca
 * del cuerpo. El permiso {@code rol.asignar} lo decide el caso de uso con los
 * permisos actuales de la base, no con los claims. Los errores los traduce
 * {@link RolesErroresAdvice}.
 */
@RestController
public class RolesController {

    private final GestionRolesPort gestionRoles;

    public RolesController(GestionRolesPort gestionRoles) {
        this.gestionRoles = gestionRoles;
    }

    /** RF-11.3, ESC-11.1–11.3: asigna un rol; 200 con el usuario y la lista completa resultante. */
    @PostMapping(path = "/api/v1/usuarios/{id}/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    public UsuarioRolesResponse asignar(@AuthenticationPrincipal ActorUsuario actor, @PathVariable("id") UUID id,
                                        @RequestBody(required = false) AsignarRolRequest cuerpo,
                                        HttpServletRequest peticion) {
        var comando = RolesWebMapper.aComando(actor, id, cuerpo, ContextoPeticiones.desde(peticion));
        return RolesWebMapper.aRespuesta(gestionRoles.asignar(comando));
    }

    /**
     * RF-11.3/11.7, ESC-11.5/11.6: revoca un rol conservando los demás; 204.
     * {@code 422 ADMINISTRADOR_PROTEGIDO} si es el propio o el último administrador activo.
     */
    @DeleteMapping("/api/v1/usuarios/{id}/roles/{rol}")
    public ResponseEntity<Void> revocar(@AuthenticationPrincipal ActorUsuario actor, @PathVariable("id") UUID id,
                                        @PathVariable("rol") String rol, HttpServletRequest peticion) {
        gestionRoles.revocar(RolesWebMapper.aComando(actor, id, rol, ContextoPeticiones.desde(peticion)));
        return ResponseEntity.noContent().build();
    }
}
