package com.marketplace.auth_service.sesion.application.port.out;

import com.marketplace.auth_service.sesion.application.dto.PrevisionCredencial;
import com.marketplace.auth_service.sesion.application.dto.RevisionCredencial;
import com.marketplace.auth_service.sesion.application.dto.SecretoEfimero;
import com.marketplace.auth_service.shared.domain.Rol;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Credenciales para el login (Juan José). Preverificación con comparación
 * equivalente para inexistentes, confirmación de revisión bajo lock y caducidad
 * al completar autenticación. Nunca entrega hash ni contraseña.
 */
public interface CredencialesLoginPort {
    PrevisionCredencial preverificar(Optional<UUID> usuarioId, SecretoEfimero secreto);
    boolean confirmarRevision(RevisionCredencial revision);
    boolean caducada(UUID usuarioId, Set<Rol> roles, Instant ahora);
}
