package com.marketplace.auth_service.shared.domain;

public record ContextoPeticion(
    String ip,
    String agente,
    String correlacionId
) {}