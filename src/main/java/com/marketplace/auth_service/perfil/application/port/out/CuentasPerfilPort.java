package com.marketplace.auth_service.perfil.application.port.out;

import com.marketplace.auth_service.perfil.application.dto.CuentaBasica;
import java.util.Optional;
import java.util.UUID;

/** Necesidad de perfil hacia cuentas (Eva). */
public interface CuentasPerfilPort {
    Optional<CuentaBasica> leer(UUID usuarioId);
}
