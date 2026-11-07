package com.marketplace.auth_service.usuario.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Identidad de una cuenta creada por el registro público (SPEC-01): nace en
 * {@code PENDIENTE_VERIFICACION}, con los términos aceptados en {@code aceptadoEn}
 * y la versión vigente de su texto (RF-01.4).
 */
public record CuentaNueva(UUID id, String correo, String nombres, String apellidos, String celular,
                          CanalOrigen canalOrigen, String versionTerminos, Instant aceptadoEn) {
    public CuentaNueva {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(correo, "correo");
        Objects.requireNonNull(nombres, "nombres");
        Objects.requireNonNull(apellidos, "apellidos");
        Objects.requireNonNull(celular, "celular");
        Objects.requireNonNull(canalOrigen, "canalOrigen");
        Objects.requireNonNull(versionTerminos, "versionTerminos");
        Objects.requireNonNull(aceptadoEn, "aceptadoEn");
    }
}
