package com.marketplace.auth_service.proteccion.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Hecho de login; no es un contador ni contiene contraseña/huella. */
public record IntentoLogin(UUID id, UUID usuarioId, String correoIntentado,
                          ResultadoLogin resultado, String ip, String agente, Instant fecha) {
    public IntentoLogin {
        Objects.requireNonNull(id);
        Objects.requireNonNull(resultado);
        Objects.requireNonNull(fecha);
        comprobarLongitud(correoIntentado, 254);
        comprobarLongitud(ip, 45);
        comprobarLongitud(agente, 255);
    }
    private static void comprobarLongitud(String valor, int maximo) {
        if (valor != null && valor.length() > maximo) {
            throw new IllegalArgumentException("Metadato de intento demasiado largo");
        }
    }
    @Override public String toString() {
        return "IntentoLogin[id=" + id + ", usuarioId=" + usuarioId + ", resultado=" + resultado + "]";
    }
}
