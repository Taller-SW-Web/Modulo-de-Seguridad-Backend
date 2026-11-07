package com.marketplace.auth_service.credencial.application.dto;

import java.util.Objects;

/** Resultado interno; el controlador siempre responde 202 salvo que la cuota se agote. */
public sealed interface ResultadoSolicitudRecuperacion
        permits ResultadoSolicitudRecuperacion.Aceptada, ResultadoSolicitudRecuperacion.Limite {
    record Aceptada() implements ResultadoSolicitudRecuperacion { }

    record Limite(long reintentarEnSegundos) implements ResultadoSolicitudRecuperacion {
        public Limite {
            if (reintentarEnSegundos < 1) throw new IllegalArgumentException("Retry-After debe ser positivo");
        }
    }
}
