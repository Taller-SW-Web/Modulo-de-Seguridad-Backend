package com.marketplace.auth_service.sesion.application.port.out;

import com.marketplace.auth_service.sesion.application.dto.CuentaLogin;

import java.util.Optional;
import java.util.UUID;

/**
 * Cuentas para el login (Eva). La resolución y el lock de la fila son suyos;
 * aquí solo viaja el snapshot, sin credencial ni documento.
 */
public interface CuentasLoginPort {
    Optional<CuentaLogin> buscarPorCorreo(String correoNormalizado);
    Optional<CuentaLogin> bloquear(UUID usuarioId);
}
