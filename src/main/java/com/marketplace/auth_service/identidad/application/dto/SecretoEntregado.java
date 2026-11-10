package com.marketplace.auth_service.identidad.application.dto;

import java.util.Objects;

/**
 * Cliente técnico junto con el secreto recién generado (SPEC-19, RF-19.4 y RF-19.6).
 * Es la única vez que el secreto sale del servidor; {@link #toString()} lo oculta
 * para que nunca llegue a un log.
 */
public record SecretoEntregado(ClienteTecnico cliente, String clientSecret) {

    public SecretoEntregado {
        Objects.requireNonNull(cliente, "cliente");
        Objects.requireNonNull(clientSecret, "clientSecret");
    }

    @Override
    public String toString() {
        return "SecretoEntregado[clientId=" + cliente.clientId() + ", clientSecret=REDACTADO]";
    }
}
