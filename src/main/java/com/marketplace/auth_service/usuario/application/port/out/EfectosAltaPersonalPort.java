package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.shared.domain.Rol;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Lo que el alta administrativa necesita de autorización (Sergio) y auditoría (Christian). */
public interface EfectosAltaPersonalPort {

    /** Ejecuta el trabajo en una transacción; si {@code confirmar} lo rechaza, revierte todo. */
    <T> T enTransaccion(Supplier<T> trabajo, Predicate<T> confirmar);

    /** {@code usuario.crear} vigente en la base, con el rol {@code ADMIN_SISTEMA}. */
    boolean puedeCrear(UUID actor);

    /**
     * Rol inicial de la cuenta recién creada, sin revocar sesiones (no tiene) ni publicar
     * {@code usuario.roles_cambiados}: la cuenta se anuncia con {@code usuario.creado} al activarse.
     */
    void asignarRolInicial(UUID usuarioId, Rol rol);

    /** {@code USUARIO_CREADO} crítico, en la transacción del alta (RF-03.4, RF-12.6). */
    void auditarCreacion(UUID usuarioId, Rol rol, ActorUsuario actor, ContextoPeticion contexto, Instant ahora);

    /** {@code USUARIO_CREADO} fallido, con el actor pero sin identificar la cuenta existente. */
    void auditarRechazo(String motivo, ActorUsuario actor, ContextoPeticion contexto, Instant ahora);

    /** {@code ACCESO_DENEGADO} no crítico: si la auditoría falla, la respuesta sigue siendo 403. */
    void denegado(ActorUsuario actor, ContextoPeticion contexto, Instant ahora);
}
