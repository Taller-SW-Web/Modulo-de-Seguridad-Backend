package com.marketplace.auth_service.usuario.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Cuenta en {@code PENDIENTE_VERIFICACION} a la que se puede reenviar el enlace (RF-02.4). */
public record CuentaPendiente(UUID id, String correo, String nombres, CanalOrigen canalOrigen) {
    public CuentaPendiente {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(correo, "correo");
        Objects.requireNonNull(nombres, "nombres");
        Objects.requireNonNull(canalOrigen, "canalOrigen");
    }
}
