package com.marketplace.auth_service.identidad.application.dto;

import java.util.List;
import java.util.Objects;

/**
 * Lo que el caso de uso de tokens de servicio entrega para firmar: el cliente
 * ya autenticado, los scopes concedidos y sus audiencias sin repetir. El
 * firmador no decide qué scopes corresponden.
 */
public record ClaimsServicio(String clientId, List<String> audiencias, List<String> scopes) {

    public ClaimsServicio {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId es obligatorio");
        }
        audiencias = List.copyOf(Objects.requireNonNull(audiencias, "audiencias"));
        scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes"));
    }
}
