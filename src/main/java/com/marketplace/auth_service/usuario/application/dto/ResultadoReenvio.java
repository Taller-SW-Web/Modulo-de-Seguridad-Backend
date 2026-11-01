package com.marketplace.auth_service.usuario.application.dto;

/**
 * Resultado de {@code POST /auth/verificar-correo/reenviar} (RF-02.4). {@code Aceptado} no dice si
 * la cuenta existe ni si se envió algo: el {@code 202} es el mismo para cualquier correo.
 */
public sealed interface ResultadoReenvio {

    enum Aceptado implements ResultadoReenvio { ACEPTADO }

    /** Cuarta solicitud o más en una hora para ese correo: {@code 429} con {@code Retry-After}. */
    record Limitado(long reintentarEnSegundos) implements ResultadoReenvio {
        public Limitado {
            if (reintentarEnSegundos < 1) throw new IllegalArgumentException("reintentarEnSegundos");
        }
    }
}
