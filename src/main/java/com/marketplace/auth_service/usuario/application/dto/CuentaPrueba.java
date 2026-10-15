package com.marketplace.auth_service.usuario.application.dto;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Identidad sintética de una cuenta de prueba (EP-01-T007, TEC-02). Solo datos de
 * identidad: la credencial y los roles los asignan sus dueños por sus capacidades.
 */
public record CuentaPrueba(UUID id, String correo, String nombres, String apellidos, boolean mfaVoluntario) {
    public CuentaPrueba {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(correo, "correo");
        Objects.requireNonNull(nombres, "nombres");
        Objects.requireNonNull(apellidos, "apellidos");
        correo = correo.strip().toLowerCase(Locale.ROOT);
    }
}
