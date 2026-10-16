package com.marketplace.auth_service.credencial.application.dto;

import java.util.Objects;

/** Resultado de {@code POST /password/cambiar} (SPEC-07). */
public sealed interface ResultadoCambioContrasena {

    enum Simple implements ResultadoCambioContrasena { CAMBIADA, ACTUAL_INCORRECTA, SESION_INVALIDA }

    record PoliticaIncumplida(ValidacionPassword validacion) implements ResultadoCambioContrasena {
        public PoliticaIncumplida {
            Objects.requireNonNull(validacion);
        }
    }
}
