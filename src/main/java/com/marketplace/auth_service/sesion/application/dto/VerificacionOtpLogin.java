package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;
import java.util.UUID;

/** Lo que el login necesita del OTP verificado por MFA (Luis); prueba interna, no viene de HTTP. */
public sealed interface VerificacionOtpLogin {

    record Verificada(UUID usuarioId, RevisionLogin revision) implements VerificacionOtpLogin {
        public Verificada {
            Objects.requireNonNull(usuarioId);
            Objects.requireNonNull(revision);
        }
    }

    record CodigoInvalido(int intentosRestantes) implements VerificacionOtpLogin { }

    enum Rechazo implements VerificacionOtpLogin { DESAFIO_INVALIDO, INTENTOS_AGOTADOS, CODIGO_EXPIRADO }
}
