package com.marketplace.auth_service.usuario.adapter.in.transaction;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAdministracionCuenta;
import com.marketplace.auth_service.usuario.application.port.in.BajaReactivacionPort;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Estado, sesiones, auditoría crítica y evento en una sola transacción (SPEC-04, RNF). */
public class BajaReactivacionTransaccional implements BajaReactivacionPort {
    private final BajaReactivacionPort delegado;

    public BajaReactivacionTransaccional(BajaReactivacionPort delegado) {
        this.delegado = delegado;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAdministracionCuenta darDeBaja(ActorUsuario actor, String usuarioId, ContextoPeticion contexto) {
        return delegado.darDeBaja(actor, usuarioId, contexto);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResultadoAdministracionCuenta reactivar(ActorUsuario actor, String usuarioId, ContextoPeticion contexto) {
        return delegado.reactivar(actor, usuarioId, contexto);
    }
}
