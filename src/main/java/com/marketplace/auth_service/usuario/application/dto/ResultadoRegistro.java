package com.marketplace.auth_service.usuario.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Resultado de {@code POST /auth/registro} (SPEC-01). Ninguna variante lleva tokens de sesión. */
public sealed interface ResultadoRegistro {

    /** Cuenta creada en {@code PENDIENTE_VERIFICACION} con rol {@code CLIENTE}. */
    record Creada(UUID id) implements ResultadoRegistro {
        public Creada {
            Objects.requireNonNull(id);
        }
    }

    /** El correo ya pertenece a una cuenta; la respuesta no lo confirma (409 neutro). */
    enum Rechazo implements ResultadoRegistro { CORREO_NO_DISPONIBLE }

    /** La contraseña no cumple la política de SPEC-07; no se crea nada. */
    record PoliticaIncumplida(List<ReglaIncumplida> reglas) implements ResultadoRegistro {
        public PoliticaIncumplida {
            reglas = List.copyOf(reglas);
        }
    }

    /** Regla de la política que falló, con el valor esperado si la regla lo publica. */
    record ReglaIncumplida(String regla, Integer esperado) {
        public ReglaIncumplida {
            Objects.requireNonNull(regla, "regla");
        }
    }
}
