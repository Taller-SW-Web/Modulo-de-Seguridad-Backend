package com.marketplace.auth_service.direccion.application.service;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.direccion.application.port.in.ConsultaDireccionesPort;
import com.marketplace.auth_service.direccion.application.port.out.DireccionesPersistidasPort;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementación real del puerto exportado, sobre el puerto de persistencia de direcciones. No conoce
 * Spring: la lectura corre en la transacción de solo lectura de {@code DireccionesPersistidasAdapter}.
 */
public final class ConsultaDireccionesService implements ConsultaDireccionesPort {
    private final DireccionesPersistidasPort persistencia;

    public ConsultaDireccionesService(DireccionesPersistidasPort persistencia) {
        this.persistencia = Objects.requireNonNull(persistencia);
    }

    @Override
    public List<DireccionDatos> consultar(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        return List.copyOf(persistencia.listar(usuarioId));
    }
}
