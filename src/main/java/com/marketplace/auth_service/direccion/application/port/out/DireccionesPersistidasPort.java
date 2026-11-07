package com.marketplace.auth_service.direccion.application.port.out;

import com.marketplace.auth_service.direccion.application.dto.DireccionDatos;
import com.marketplace.auth_service.direccion.application.port.in.DireccionesPort.DatosNuevaDireccion;
import java.util.List;
import java.util.UUID;

public interface DireccionesPersistidasPort {
    List<DireccionDatos> listar(UUID usuarioId);
    DireccionDatos crear(UUID usuarioId, DatosNuevaDireccion datos);
}
