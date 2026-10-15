package com.marketplace.auth_service.sesion.application.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Sesión iniciada (SPEC-05 RF-05.6): acceso RS256 de 900 s firmado por Sergio y
 * refresco opaco de 7 días que abre una familia nueva. El secreto del refresco
 * viaja una sola vez; en DB solo queda su hash. Sin tokens en logs.
 */
public record SesionIniciada(String accessToken, String refreshToken, long expiresIn,
                             Instant expiraAccesoEn, Instant expiraRefrescoEn,
                             UsuarioAutenticado usuario) {
    public SesionIniciada {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(refreshToken, "refreshToken");
        Objects.requireNonNull(expiraAccesoEn, "expiraAccesoEn");
        Objects.requireNonNull(expiraRefrescoEn, "expiraRefrescoEn");
        Objects.requireNonNull(usuario, "usuario");
        if (expiresIn < 0) {
            throw new IllegalArgumentException("expiresIn no es negativo");
        }
    }

    @Override
    public String toString() {
        return "SesionIniciada[usuario=" + usuario.id() + ", expiraAccesoEn=" + expiraAccesoEn + "]";
    }
}
