package com.marketplace.auth_service.usuario.application.dto;

import java.util.Objects;
import java.util.UUID;

/** Resultado de {@code POST /api/v2/usuarios} (SPEC-03 RF-03.1). */
public sealed interface ResultadoAltaPersonal {

    /** Cuenta creada en {@code PENDIENTE_VERIFICACION} con su enlace de activación encolado. */
    record Creada(UUID id) implements ResultadoAltaPersonal {
        public Creada {
            Objects.requireNonNull(id);
        }
    }

    enum Rechazo implements ResultadoAltaPersonal {
        /** Sin {@code usuario.crear} vigente: 403 y {@code ACCESO_DENEGADO}. */
        SIN_PERMISO,
        /** El correo ya pertenece a una cuenta (ESC-03.3): 409, sin crear nada ni publicar eventos. */
        CORREO_NO_DISPONIBLE
    }
}
