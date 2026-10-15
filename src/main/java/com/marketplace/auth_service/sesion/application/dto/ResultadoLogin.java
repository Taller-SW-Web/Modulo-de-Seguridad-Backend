package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;

/**
 * Resultado cerrado del login (SPEC-05, EP-02-T006). Los rechazos no dicen por qué:
 * inexistente, contraseña errónea, bloqueada, inactiva o sin verificar son el mismo
 * {@link Rechazo#CREDENCIALES_INVALIDAS} (RF-05.4, RF-14.6).
 */
public sealed interface ResultadoLogin {

    record Iniciada(SesionIniciada sesion) implements ResultadoLogin {
        public Iniciada {
            Objects.requireNonNull(sesion, "sesion");
        }
    }

    /** Segundo factor efectivo: solo desafío, sin familia ni reinicio de racha. */
    record DesafioRequerido(DesafioEmitido desafio) implements ResultadoLogin {
        public DesafioRequerido {
            Objects.requireNonNull(desafio, "desafio");
        }
    }

    /** Segundo factor: código incorrecto con intentos restantes (SPEC-09 ESC-09.4). */
    record CodigoInvalido(int intentosRestantes) implements ResultadoLogin { }

    enum Rechazo implements ResultadoLogin {
        CREDENCIALES_INVALIDAS, CONTRASENA_CADUCADA,
        /** Desafío MFA inexistente, vencido, manipulado o ya no aplicable (ESC-09.11). */
        DESAFIO_INVALIDO,
        OTP_INTENTOS_AGOTADOS,
        OTP_EXPIRADO
    }
}
