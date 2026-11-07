package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoAdministracionCuenta;

/** Baja lógica y reactivación administrativas (SPEC-04, FEAT-02). Abre su propia transacción. */
public interface BajaReactivacionPort {
    ResultadoAdministracionCuenta darDeBaja(ActorUsuario actor, String usuarioId, ContextoPeticion contexto);

    ResultadoAdministracionCuenta reactivar(ActorUsuario actor, String usuarioId, ContextoPeticion contexto);
}
