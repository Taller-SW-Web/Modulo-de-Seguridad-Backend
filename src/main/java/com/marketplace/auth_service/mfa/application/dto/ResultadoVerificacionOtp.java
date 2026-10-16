package com.marketplace.auth_service.mfa.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Resultado de verificar el código del desafío de login (SPEC-09 RF-09.5, RF-09.7).
 * {@link Verificado} es la prueba interna de éxito: el coordinador del login la usa en la
 * misma transacción para revalidar y abrir la sesión; no se construye desde HTTP.
 */
public sealed interface ResultadoVerificacionOtp {

    record Verificado(UUID usuarioId, RevisionCredencialMfa revision) implements ResultadoVerificacionOtp {
        public Verificado {
            Objects.requireNonNull(usuarioId);
            Objects.requireNonNull(revision);
        }
    }

    record CodigoInvalido(int intentosRestantes) implements ResultadoVerificacionOtp { }

    enum Rechazo implements ResultadoVerificacionOtp { DESAFIO_INVALIDO, INTENTOS_AGOTADOS, CODIGO_EXPIRADO }
}
