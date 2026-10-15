package com.marketplace.auth_service.autorizacion.application.dto;

import com.marketplace.auth_service.shared.domain.Rol;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de un cambio de rol ya confirmado.
 *
 * @param cuenta     la cuenta objetivo, leída bajo lock
 * @param roles      lista completa resultante, en el orden del catálogo
 * @param huboCambio {@code false} si el rol ya estaba (asignar) o ya no estaba (revocar):
 *                   en ese caso no se revocan sesiones, ni se audita, ni se publica evento
 */
public record RolesCambiados(CuentaRoles cuenta, List<Rol> roles, boolean huboCambio) {

    public RolesCambiados {
        Objects.requireNonNull(cuenta, "cuenta");
        roles = List.copyOf(roles);
    }
}
