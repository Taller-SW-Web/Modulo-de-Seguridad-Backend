package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.usuario.domain.model.CuentaNueva;

/** Inserta la identidad de una cuenta registrada. Requiere la transacción del llamador. */
public interface AltaCuentaPort {
    /**
     * Crea la cuenta y devuelve {@code true}. Si el correo ya pertenece a otra cuenta,
     * incluso por una inserción concurrente, no crea nada y devuelve {@code false}.
     */
    boolean crear(CuentaNueva cuenta);
}
