package com.marketplace.auth_service.usuario.application.dto;

import java.util.List;

/**
 * Lo que la consulta de cuentas necesita de autorización para cada fila.
 *
 * @param roles       códigos del catálogo, en orden canónico
 * @param deGestion   si alguno es de gestión: entonces el segundo factor es obligatorio
 */
public record RolesDeCuenta(List<String> roles, boolean deGestion) {

    public static final RolesDeCuenta NINGUNO = new RolesDeCuenta(List.of(), false);

    public RolesDeCuenta {
        roles = List.copyOf(roles);
    }
}
