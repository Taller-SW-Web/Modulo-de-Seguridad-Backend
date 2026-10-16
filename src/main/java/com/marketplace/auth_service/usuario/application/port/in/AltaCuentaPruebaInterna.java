package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.usuario.application.dto.CuentaPrueba;

/**
 * Alta técnica de cuentas de prueba (EP-01-T007). Solo existe cuando la carga de
 * cuentas de prueba está habilitada; no es el registro público ni emite
 * {@code usuario.creado}. Requiere la transacción del llamador.
 */
public interface AltaCuentaPruebaInterna {
    /**
     * Crea la cuenta si su UUID no existe y devuelve {@code true}. Si ya existe, no
     * cambia nada y devuelve {@code false}. Si el correo pertenece a otra cuenta,
     * lanza {@link IllegalStateException} sin crear nada.
     */
    boolean asegurar(CuentaPrueba cuenta);
}
