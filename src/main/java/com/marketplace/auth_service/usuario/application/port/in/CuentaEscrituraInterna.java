package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import java.util.Set;
import java.util.UUID;

public interface CuentaEscrituraInterna {
    CuentaSnapshot actualizarBasicos(UUID usuarioId, CambioBasicos cambio);
    void guardarContadores(UUID usuarioId, ContadoresProteccion contadores);
    void guardarMfaVoluntario(UUID usuarioId, boolean habilitado);
}