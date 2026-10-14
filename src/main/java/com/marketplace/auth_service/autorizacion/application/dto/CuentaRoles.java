package com.marketplace.auth_service.autorizacion.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Lo que la gestión de roles necesita de una cuenta. Lo entrega la capacidad de
 * cuentas (Eva) a través de {@code CuentasRolesPort}; aquí no se lee la tabla
 * {@code usuario}.
 *
 * @param id             UUID de la cuenta
 * @param nombreCompleto para la respuesta {@code Usuario} del contrato
 * @param correo         para la respuesta {@code Usuario} del contrato
 * @param estado         estado <b>efectivo</b> del contrato ({@code ACTIVO}, {@code BLOQUEADO}...);
 *                       un bloqueo automático vencido ya cuenta como {@code ACTIVO}
 */
public record CuentaRoles(UUID id, String nombreCompleto, String correo, String estado) {

    public static final String ACTIVO = "ACTIVO";

    public CuentaRoles {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(estado, "estado");
    }

    public boolean activa() {
        return ACTIVO.equals(estado);
    }

    /** Sin nombre ni correo: este objeto puede acabar en un log. */
    @Override
    public String toString() {
        return "CuentaRoles[id=" + id + ", estado=" + estado + "]";
    }
}
