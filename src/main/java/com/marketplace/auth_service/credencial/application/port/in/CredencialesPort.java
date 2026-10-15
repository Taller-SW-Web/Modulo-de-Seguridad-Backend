package com.marketplace.auth_service.credencial.application.port.in;

import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.shared.domain.Rol;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** ACUERDOS-INTERNOS §2. No devuelve hashes, entidades ni secretos. */
public interface CredencialesPort {
    PreverificacionCredencial preverificar(Optional<UUID> usuarioId, SecretoEfimero secreto);
    /** Requiere transacción del coordinador y cuenta ya bloqueada por CuentasPort. */
    boolean confirmarRevision(RevisionCredencial revision);
    /** true si la contraseña está caducada; roles resueltos por autorización. */
    boolean evaluarCaducidad(UUID usuarioId, Set<Rol> roles, Instant instante);
    ValidacionPassword validarNueva(UUID usuarioId, SecretoEfimero secreto);
}
