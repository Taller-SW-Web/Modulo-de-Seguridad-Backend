package com.marketplace.auth_service.perfil.adapter.in.web;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.port.in.PerfilPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code actualizarAtributosUsuario} para la edición PROPIA (SPEC-16 RF-16.2/16.9,
 * EP-08-T004). En H3 solo el titular edita nombres, apellidos y celular: otro
 * {@code id} o un atributo privilegiado responde 403 {@code SCOPE_INSUFICIENTE}
 * antes de llamar al servicio, así que no hay efecto parcial. La rama de edición
 * administrativa queda fuera de este incremento.
 *
 * <p>Pendiente para cerrar T004 (EP-06, Christian): evento
 * {@code usuario.atributos_actualizados} por outbox en el mismo commit y auditoría no
 * crítica {@code ATRIBUTOS_ACTUALIZADOS}/acceso denegado.
 */
@RestController
public class AtributosPropiosController {

    private final PerfilPort perfil;

    public AtributosPropiosController(PerfilPort perfil) {
        this.perfil = perfil;
    }

    @PatchMapping(path = "/api/v1/usuarios/{id}/atributos",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> actualizar(
            @AuthenticationPrincipal ActorUsuario actor,
            @PathVariable("id") UUID id,
            @RequestBody Map<String, Object> cuerpo) {
        if (!actor.esTitularDe(id)) {
            throw new EdicionNoPermitidaException();
        }
        CambioAtributos cambio = AtributosPropiosWebMapper.aCambio(cuerpo);
        return PerfilWebMapper.aPerfilUsuario(perfil.actualizarPropio(actor, cambio));
    }
}
