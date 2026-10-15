package com.marketplace.auth_service.usuario.application.port.in;

public record ContadoresProteccion(int bloqueosSeguidos, int intentosFallidos) {
    public ContadoresProteccion {
        if (bloqueosSeguidos < 0 || intentosFallidos < 0)
            throw new IllegalArgumentException("Los contadores no pueden ser negativos");
    }
}
