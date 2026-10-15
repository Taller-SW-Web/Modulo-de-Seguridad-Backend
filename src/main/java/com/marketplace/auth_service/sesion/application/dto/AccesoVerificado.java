package com.marketplace.auth_service.sesion.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Acceso verificado para resolver la sesión (DAT-01). Lo arma el coordinador
 * desde el {@code AccesoValidado} de identidad: firma, issuer y vigencia ya
 * comprobados. Trae sub y jti, nunca un familiaId: la familia sale de la
 * asociación persistida, no de la petición.
 */
public record AccesoVerificado(UUID sub, UUID jti) {
    public AccesoVerificado {
        Objects.requireNonNull(sub, "sub");
        Objects.requireNonNull(jti, "jti");
    }
}
