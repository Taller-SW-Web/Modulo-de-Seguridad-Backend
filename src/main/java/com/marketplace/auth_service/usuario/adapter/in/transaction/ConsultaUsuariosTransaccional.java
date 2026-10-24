package com.marketplace.auth_service.usuario.adapter.in.transaction;

import com.marketplace.auth_service.shared.domain.ActorServicio;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.usuario.application.dto.ConsultaUsuariosQuery;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.dto.UsuarioServicioVista;
import com.marketplace.auth_service.usuario.application.dto.UsuarioVista;
import com.marketplace.auth_service.usuario.application.port.in.ConsultaUsuariosPort;
import org.springframework.transaction.annotation.Transactional;

/**
 * Una transacción de solo lectura por consulta: permiso, página y roles se leen
 * en la misma conexión, sin abrir una por cada proveedor.
 */
public class ConsultaUsuariosTransaccional implements ConsultaUsuariosPort {

    private final ConsultaUsuariosPort casoDeUso;

    public ConsultaUsuariosTransaccional(ConsultaUsuariosPort casoDeUso) {
        this.casoDeUso = casoDeUso;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResultado<UsuarioVista> listar(ActorUsuario actor, ConsultaUsuariosQuery consulta) {
        return casoDeUso.listar(actor, consulta);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioVista obtener(ActorUsuario actor, String idTexto) {
        return casoDeUso.obtener(actor, idTexto);
    }

    /** No es de solo lectura: la auditoría crítica (RF-18.4) escribe dentro de esta transacción. */
    @Override
    @Transactional
    public UsuarioServicioVista obtenerParaServicio(ActorServicio actor, String idTexto) {
        return casoDeUso.obtenerParaServicio(actor, idTexto);
    }
}
