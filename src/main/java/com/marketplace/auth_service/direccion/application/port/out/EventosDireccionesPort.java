package com.marketplace.auth_service.direccion.application.port.out;

import java.util.UUID;

public interface EventosDireccionesPort {
    void direccionesActualizadas(UUID usuarioId);
}
