package com.marketplace.auth_service.credencial.application.dto;

import java.util.Objects;

/** Resultado de POST /api/v1/password/restablecer (SPEC-08). */
public sealed interface ResultadoRestablecimiento {
    enum Simple implements ResultadoRestablecimiento { RESTABLECIDA, TOKEN_INVALIDO, TOKEN_EXPIRADO }
    record PoliticaIncumplida(ValidacionPassword validacion) implements ResultadoRestablecimiento {
        public PoliticaIncumplida { Objects.requireNonNull(validacion); }
    }
}
