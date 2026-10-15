package com.marketplace.auth_service.usuario.adapter.in.web;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.usuario.adapter.in.web.dto.PaginaUsuariosResponse;
import com.marketplace.auth_service.usuario.adapter.in.web.dto.UsuarioResponse;
import com.marketplace.auth_service.usuario.application.dto.ConsultaUsuariosQuery;
import com.marketplace.auth_service.usuario.application.port.in.ConsultaUsuariosPort;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta administrativa de cuentas: {@code listarUsuarios} (EP-01-T003) y
 * {@code obtenerUsuario} con token de persona (EP-01-T004).
 *
 * <p>El actor sale del acceso validado por el filtro. Los parámetros llegan como
 * texto y los valida el caso de uso después de autorizar. Los errores los traduce
 * {@link UsuariosErroresAdvice}; el 403 lo produce el advice común de autorización.
 */
@RestController
public class UsuariosController {

    private final ConsultaUsuariosPort consulta;

    public UsuariosController(ConsultaUsuariosPort consulta) {
        this.consulta = consulta;
    }

    @GetMapping(path = "/api/v1/usuarios", produces = MediaType.APPLICATION_JSON_VALUE)
    public PaginaUsuariosResponse listar(@AuthenticationPrincipal ActorUsuario actor,
                                         @RequestParam(name = "estado", required = false) String estado,
                                         @RequestParam(name = "rol", required = false) String rol,
                                         @RequestParam(name = "correo", required = false) String correo,
                                         @RequestParam(name = "pagina", required = false) String pagina,
                                         @RequestParam(name = "tamano", required = false) String tamano) {
        var parametros = new ConsultaUsuariosQuery(estado, rol, correo, pagina, tamano);
        return UsuariosWebMapper.aRespuesta(consulta.listar(actor, parametros));
    }

    @GetMapping(path = "/api/v1/usuarios/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public UsuarioResponse obtener(@AuthenticationPrincipal ActorUsuario actor, @PathVariable("id") String id) {
        return UsuariosWebMapper.aRespuesta(consulta.obtener(actor, id));
    }
}
