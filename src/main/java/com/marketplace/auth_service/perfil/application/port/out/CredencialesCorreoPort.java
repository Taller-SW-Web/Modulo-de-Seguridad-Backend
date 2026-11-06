package com.marketplace.auth_service.perfil.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Comprobación de la contraseña actual, delegada a quien posee las credenciales. */
public interface CredencialesCorreoPort {

    /** Revisión opaca de la credencial comprobada; solo se devuelve a {@link #confirmarRevision}. */
    interface RevisionContrasena {}

    /** Comparación costosa (Argon2id): se hace antes de abrir la transacción. Vacío si no coincide. */
    Optional<RevisionContrasena> preverificar(UUID usuarioId, String contrasenaActual);

    /** Dentro de la transacción y con la cuenta bloqueada: false si la credencial cambió entretanto. */
    boolean confirmarRevision(RevisionContrasena revision);
}
