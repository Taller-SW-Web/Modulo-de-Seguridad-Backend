package com.marketplace.auth_service.mfa.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EfectosCambioMfaPort {
    Optional<UUID> familia(ActorUsuario actor);
    void guardarEstado(UUID usuario, boolean habilitado);
    void enviarCodigo(String correo, String codigo);
    void avisarCambio(String correo, boolean habilitado);
    void auditar(String accion, boolean exito, boolean critica, ActorUsuario actor,
            ContextoPeticion contexto, Instant fecha);
}
