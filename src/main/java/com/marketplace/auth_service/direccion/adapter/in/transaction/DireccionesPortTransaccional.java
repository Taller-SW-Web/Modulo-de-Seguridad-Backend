package com.marketplace.auth_service.direccion.adapter.in.transaction;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecturas de solo lectura; el alta abre REQUIRED/READ_COMMITTED. La transacción del alta
 * cubre también el outbox, así el evento se confirma o se descarta junto con la dirección.
 */
@Transactional(readOnly = true)
public class DireccionesPortTransaccional implements DireccionesPort {
    private final DireccionesPort delegado;

    public DireccionesPortTransaccional(DireccionesPort delegado) {
        this.delegado = delegado;
    }

    @Override
    public List<DireccionDatos> listar(ActorUsuario actor, UUID usuarioId) {
        return delegado.listar(actor, usuarioId);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DireccionDatos crear(ActorUsuario actor, UUID usuarioId, DatosNuevaDireccion datos) {
        return delegado.crear(actor, usuarioId, datos);
    }
}
