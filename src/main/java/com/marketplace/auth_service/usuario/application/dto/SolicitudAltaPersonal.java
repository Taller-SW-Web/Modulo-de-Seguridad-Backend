package com.marketplace.auth_service.usuario.application.dto;

import com.marketplace.auth_service.shared.domain.Rol;
import java.util.Locale;
import java.util.Objects;

/**
 * Datos del alta administrativa (SPEC-03 RF-03.1, {@code POST /api/v2/usuarios}) ya validados en forma
 * por el adaptador HTTP. El correo se normaliza como en el resto de cuentas.
 *
 * @param rol uno de los cinco roles de personal, o {@code null} para una cuenta sin rol
 */
public record SolicitudAltaPersonal(String correo, String nombres, String apellidos, Rol rol) {
    public SolicitudAltaPersonal {
        Objects.requireNonNull(correo, "correo");
        Objects.requireNonNull(nombres, "nombres");
        Objects.requireNonNull(apellidos, "apellidos");
        if (rol == Rol.CLIENTE) {
            throw new IllegalArgumentException("El alta administrativa no crea clientes");
        }
        correo = correo.strip().toLowerCase(Locale.ROOT);
        nombres = nombres.strip();
        apellidos = apellidos.strip();
    }

    @Override
    public String toString() {
        return "SolicitudAltaPersonal[rol=" + rol + ", REDACTADO]";
    }
}
