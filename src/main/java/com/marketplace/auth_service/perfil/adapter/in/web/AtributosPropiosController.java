package com.marketplace.auth_service.perfil.adapter.in.web;

import com.marketplace.auth_service.perfil.application.dto.CambioAdministrativo;
import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.port.in.PerfilPort;
import com.marketplace.auth_service.shared.adapter.web.ContextoPeticiones;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code actualizarAtributosUsuario} (SPEC-16 RF-16.2/16.8/16.9). Dos ramas:
 * <ul>
 *   <li><b>Propia</b> (EP-08-T004): el titular edita nombres, apellidos y celular.</li>
 *   <li><b>Administrativa</b> (FEAT-04-T6): otro {@code id}, o un atributo de documento o de rol,
 *   exige {@code atributos.editar} revalidado contra la base, también sobre la cuenta propia.
 *   Sin permiso, 403 {@code SCOPE_INSUFICIENTE} antes de leer o escribir nada.</li>
 * </ul>
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
            @RequestBody Map<String, Object> cuerpo,
            HttpServletRequest peticion) {
        if (actor.esTitularDe(id) && !AtributosPropiosWebMapper.tienePrivilegiados(cuerpo)) {
            CambioAtributos cambio = AtributosPropiosWebMapper.aCambio(cuerpo);
            return PerfilWebMapper.aPerfilUsuario(perfil.actualizarPropio(actor, cambio));
        }
        CambioAdministrativo cambio = AtributosAdministrativosWebMapper.aCambio(cuerpo);
        return PerfilWebMapper.aPerfilUsuario(
                perfil.actualizarComoAdministrador(actor, id, cambio, ContextoPeticiones.desde(peticion)));
    }
}
