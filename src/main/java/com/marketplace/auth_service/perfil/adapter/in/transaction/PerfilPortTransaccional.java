package com.marketplace.auth_service.perfil.adapter.in.transaction;

import com.marketplace.auth_service.perfil.application.dto.CambioAtributos;
import com.marketplace.auth_service.perfil.application.dto.PerfilProyectado;
import com.marketplace.auth_service.perfil.application.port.in.PerfilPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.UUID;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Lecturas de solo lectura; la edición abre REQUIRED/READ_COMMITTED (ACUERDOS-INTERNOS §3). */
@Transactional(readOnly = true)
public class PerfilPortTransaccional implements PerfilPort {
    private final PerfilPort delegado;

    public PerfilPortTransaccional(PerfilPort delegado) {
        this.delegado = delegado;
    }

    @Override
    public PerfilProyectado leerPropio(ActorUsuario actor) {
        return delegado.leerPropio(actor);
    }

    @Override
    public PerfilProyectado proyectarPara(ActorUsuario actor, UUID usuarioId) {
        return delegado.proyectarPara(actor, usuarioId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PerfilProyectado actualizarPropio(ActorUsuario actor, CambioAtributos cambio) {
        return delegado.actualizarPropio(actor, cambio);
    }
}
