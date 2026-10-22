package com.marketplace.auth_service.identidad.application.dto;

/**
 * Petición {@code client_credentials} tal como llega (SPEC-17 RF-17.4). El
 * scope pedido no cambia lo concedido: se conceden siempre los asignados al
 * cliente (RF-17.5). {@code toString} nunca incluye el secreto.
 */
public record SolicitudTokenServicio(String grantType, String clientId, String clientSecret, String scope) {

    public static final String CLIENT_CREDENTIALS = "client_credentials";

    @Override
    public String toString() {
        return "SolicitudTokenServicio[grantType=" + grantType + ", clientId=" + clientId + ", clientSecret=REDACTADO]";
    }
}
