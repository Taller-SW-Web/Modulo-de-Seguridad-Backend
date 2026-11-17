package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.usuario.domain.model.CuentaPersonalNueva;
import java.time.Instant;
import java.util.UUID;

/** Identidad de las cuentas creadas por otra persona (SPEC-03). Requiere la transacción del llamador. */
public interface CuentasPersonalPort {

    /**
     * Inserta la cuenta {@code PENDIENTE_VERIFICACION} sin términos aceptados y devuelve {@code true}.
     * Si el correo ya pertenece a otra cuenta, incluso por una inserción concurrente, no crea nada y
     * devuelve {@code false}.
     */
    boolean crear(CuentaPersonalNueva cuenta);

    /**
     * Bloquea la fila y devuelve {@code true} solo si la cuenta sigue {@code PENDIENTE_VERIFICACION} y
     * todavía no aceptó los términos, es decir, si la creó otra persona y nadie la activó.
     */
    boolean bloquearSinActivar(UUID usuarioId);

    /** Pasa la cuenta ya bloqueada a {@code ACTIVO}, con el correo verificado y los términos aceptados. */
    void activar(UUID usuarioId, String versionTerminos, Instant ahora);
}
