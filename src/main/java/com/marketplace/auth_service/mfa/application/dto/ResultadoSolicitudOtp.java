package com.marketplace.auth_service.mfa.application.dto;

import java.util.Objects;

/** Resultado de {@code POST /auth/otp/solicitar} (SPEC-09 RF-09.6, RF-09.14–09.16). */
public sealed interface ResultadoSolicitudOtp {

    record Enviado(CanalMfa canal, String destinoEnmascarado, int expiraEn, int desafioExpiraEn)
            implements ResultadoSolicitudOtp {
        public Enviado {
            Objects.requireNonNull(canal);
            Objects.requireNonNull(destinoEnmascarado);
        }
    }

    /** Tres envíos en 15 minutos: espera en segundos (≥ 1) hasta liberar cuota. */
    record Limite(long reintentarEn) implements ResultadoSolicitudOtp { }

    enum Rechazo implements ResultadoSolicitudOtp { DESAFIO_INVALIDO, SIN_CELULAR }
}
