package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Permiso;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Lo que la baja y la reactivación necesitan de autorización (Sergio), sesiones y auditoría. */
public interface EfectosBajaReactivacionPort {

    /** Permiso vigente del actor según sus roles actuales, no los del token. */
    boolean autorizado(UUID actor, Permiso permiso);

    /** Serializa los cambios que afectan a «último ADMIN_SISTEMA activo» (SPEC-11/15/04). */
    void bloquearCatalogoAdministrador();

    boolean esAdministrador(UUID usuarioId);

    Set<UUID> administradores();

    /** Revoca todas las familias de sesión en la transacción de la baja (RF-04.1). */
    void revocarSesiones(UUID usuarioId);

    /** {@code USUARIO_DESACTIVADO} crítico y {@code usuario.desactivado} por el outbox. */
    void registrarBaja(UUID usuarioId, ActorUsuario actor, ContextoPeticion contexto, Instant ahora);

    /** {@code USUARIO_REACTIVADO} crítico y {@code usuario.reactivado} con los roles actuales. */
    void registrarReactivacion(UUID usuarioId, ActorUsuario actor, ContextoPeticion contexto, Instant ahora);

    /** {@code ACCESO_DENEGADO} no crítico: su fallo no cambia el 403. */
    void denegado(ActorUsuario actor, ContextoPeticion contexto, Instant ahora);
}
