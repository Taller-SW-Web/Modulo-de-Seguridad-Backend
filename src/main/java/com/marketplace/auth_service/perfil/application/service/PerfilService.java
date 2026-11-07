package com.marketplace.auth_service.perfil.application.service;

import com.marketplace.auth_service.perfil.application.dto.CambioAdministrativo;
import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.in.PerfilPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.Objects;
import java.util.UUID;

/** Implementa PerfilPort componiendo los casos de uso. La transacción la pone el decorador. */
public final class PerfilService implements PerfilPort {
    private final ConsultarPerfil consulta;
    private final EditarPerfilPropio edicion;
    private final EditarAtributosAdministrativo edicionAdministrativa;

    public PerfilService(ConsultarPerfil consulta, EditarPerfilPropio edicion,
                         EditarAtributosAdministrativo edicionAdministrativa) {
        this.consulta = Objects.requireNonNull(consulta, "consulta");
        this.edicion = Objects.requireNonNull(edicion, "edicion");
        this.edicionAdministrativa = Objects.requireNonNull(edicionAdministrativa, "edicionAdministrativa");
    }

    @Override
    public PerfilProyectado leerPropio(ActorUsuario actor) {
        return consulta.leerPropio(actor);
    }

    @Override
    public PerfilProyectado proyectarPara(ActorUsuario actor, UUID usuarioId) {
        return consulta.proyectarPara(actor, usuarioId);
    }

    @Override
    public PerfilProyectado actualizarPropio(ActorUsuario actor, CambioAtributos cambio) {
        return edicion.actualizarPropio(actor, cambio);
    }

    @Override
    public PerfilProyectado actualizarComoAdministrador(ActorUsuario actor, UUID usuarioId,
                                                        CambioAdministrativo cambio, ContextoPeticion contexto) {
        return edicionAdministrativa.actualizar(actor, usuarioId, cambio, contexto);
    }
}
