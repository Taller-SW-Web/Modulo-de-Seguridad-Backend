package com.marketplace.auth_service.perfil.adapter.in.web;

import com.marketplace.auth_service.perfil.application.port.in.PerfilPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code obtenerPerfilPropio} (SPEC-16 RF-16.1, EP-08-T003). El titular sale del
 * {@code sub} del acceso validado: no hay parámetro ni cuerpo que elija otra
 * cuenta. Un token de servicio no llega aquí: el filtro responde 403
 * {@code TOKEN_NO_APLICABLE}.
 */
@RestController
public class PerfilController {

    private final PerfilPort perfil;

    public PerfilController(PerfilPort perfil) {
        this.perfil = perfil;
    }

    @GetMapping(path = "/api/v1/auth/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> propio(@AuthenticationPrincipal ActorUsuario actor) {
        return PerfilWebMapper.aPerfilUsuario(perfil.leerPropio(actor));
    }
}
