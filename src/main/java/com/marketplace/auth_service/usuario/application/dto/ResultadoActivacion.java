package com.marketplace.auth_service.usuario.application.dto;

import java.util.List;

/**
 * Resultado de {@code POST /api/v1/auth/activar-cuenta} (SPEC-03 RF-03.5). {@code INVALIDA} (token
 * desconocido) se responde igual que {@code YA_USADA}: el contrato solo publica los dos 410 de enlace,
 * como en la verificación de correo.
 */
public sealed interface ResultadoActivacion {

    enum Estado implements ResultadoActivacion { ACTIVADA, EXPIRADA, YA_USADA, INVALIDA }

    /** La contraseña no cumple SPEC-07 (ESC-03.4): no cambia nada y el enlace sigue sirviendo. */
    record PoliticaIncumplida(List<ResultadoRegistro.ReglaIncumplida> reglas) implements ResultadoActivacion {
        public PoliticaIncumplida {
            reglas = List.copyOf(reglas);
        }
    }
}
