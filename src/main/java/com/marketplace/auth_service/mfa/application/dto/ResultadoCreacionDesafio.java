package com.marketplace.auth_service.mfa.application.dto;

import java.util.Objects;

/** Resultados internos; el login conserva sus respuestas públicas y traduce tras commit. */
public sealed interface ResultadoCreacionDesafio {
    record Creado(DesafioLogin desafio) implements ResultadoCreacionDesafio {
        public Creado { Objects.requireNonNull(desafio); }
    }
    record NoRequerido(EstadoMfa estado) implements ResultadoCreacionDesafio {
        public NoRequerido {
            Objects.requireNonNull(estado);
            if (estado.mfaHabilitado()) throw new IllegalArgumentException("MFA efectivo requiere desafío");
        }
    }
    /** No distingue inexistencia, indisponibilidad o revisión sustituida. */
    enum Rechazado implements ResultadoCreacionDesafio { INVALIDO }
}
