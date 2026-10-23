package com.marketplace.auth_service.proteccion.application.port.out;

import com.marketplace.auth_service.proteccion.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.UUID;

public interface OperacionManualPort {
    boolean autorizado(ActorUsuario actor);
    boolean esAdministrador(UUID usuario);
    long otrosAdministradoresActivos(UUID usuario);
    void revocarSesiones(UUID usuario);
    void registrar(boolean bloquear, CuentaSnapshot cuenta, ActorUsuario actor, ContextoPeticion contexto, Instant fecha);
    void denegado(ActorUsuario actor, ContextoPeticion contexto, Instant fecha);
}
