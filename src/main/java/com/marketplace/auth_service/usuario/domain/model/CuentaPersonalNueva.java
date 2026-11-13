package com.marketplace.auth_service.usuario.domain.model;

import com.marketplace.auth_service.shared.domain.Rol;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuenta de personal que da de alta un {@code ADMIN_SISTEMA} (SPEC-03 RF-03.1, FEAT-13-T1): nace en
 * {@code PENDIENTE_VERIFICACION}, sin contraseña, sin celular y <b>sin términos aceptados</b>; la
 * persona los acepta al activarla con su enlace (RF-03.5). {@code rol} es {@code null} para una
 * cuenta sin rol (repartidor de Despacho, ESC-03.6) y nunca es {@code CLIENTE}.
 */
public record CuentaPersonalNueva(UUID id, String correo, String nombres, String apellidos, Rol rol,
                                  Instant creadaEn) {
    public CuentaPersonalNueva {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(correo, "correo");
        Objects.requireNonNull(nombres, "nombres");
        Objects.requireNonNull(apellidos, "apellidos");
        Objects.requireNonNull(creadaEn, "creadaEn");
        if (rol == Rol.CLIENTE) {
            throw new IllegalArgumentException("El alta administrativa no crea clientes");
        }
    }

    @Override
    public String toString() {
        return "CuentaPersonalNueva[id=" + id + ", rol=" + rol + ", REDACTADO]";
    }
}
