package com.marketplace.auth_service.sesion.adapter.out.capacidad;

import com.marketplace.auth_service.sesion.application.dto.PrevisionCredencial;
import com.marketplace.auth_service.sesion.application.dto.RevisionCredencial;
import com.marketplace.auth_service.sesion.application.dto.SecretoEfimero;
import com.marketplace.auth_service.sesion.application.port.out.CredencialesLoginPort;
import com.marketplace.auth_service.sesion.domain.exception.DependenciaNoDisponibleException;
import com.marketplace.auth_service.shared.domain.Rol;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Marcador hasta que Juan José entregue {@code CredencialesPort}:
 * preverificación equivalente, revisión y caducidad son suyos. No se duplica
 * su validación; el puente real lo reemplaza en composición (T007).
 */
public class CredencialesLoginPendiente implements CredencialesLoginPort {

    @Override
    public PrevisionCredencial preverificar(Optional<UUID> usuarioId, SecretoEfimero secreto) {
        throw new DependenciaNoDisponibleException("CredencialesPort de Juan José");
    }

    @Override
    public boolean confirmarRevision(RevisionCredencial revision) {
        throw new DependenciaNoDisponibleException("CredencialesPort de Juan José");
    }

    @Override
    public boolean caducada(UUID usuarioId, Set<Rol> roles, Instant ahora) {
        throw new DependenciaNoDisponibleException("CredencialesPort de Juan José");
    }
}
