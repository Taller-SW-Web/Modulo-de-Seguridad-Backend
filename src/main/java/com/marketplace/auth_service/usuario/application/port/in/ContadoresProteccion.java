package com.marketplace.auth_service.usuario.application.port.in;

public record ContadoresProteccion(
    int bloqueosSeguidos,
    int intentosFallidos
) {}