package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;

/**
 * Resultado del login (SPEC-05): éxito con sesión, desafío MFA sin tokens,
 * rechazo genérico (mismo 401 para errónea/inexistente/bloqueada/inactiva/
 * pendiente) o caducidad (403 solo al completar autenticación de gestión).
 */
public sealed interface ResultadoLogin permits ResultadoLogin.Exito, ResultadoLogin.Desafio,
        ResultadoLogin.Rechazado, ResultadoLogin.Caducada {

    record Exito(SesionIniciada sesion) implements ResultadoLogin {
        public Exito {
            Objects.requireNonNull(sesion, "sesion");
        }
    }

    record Desafio(DesafioMfaRespuesta desafio) implements ResultadoLogin {
        public Desafio {
            Objects.requireNonNull(desafio, "desafio");
        }
    }

    record Rechazado() implements ResultadoLogin {
    }

    record Caducada() implements ResultadoLogin {
    }
}
