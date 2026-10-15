package com.marketplace.auth_service.sesion.adapter.out.capacidad;

import com.marketplace.auth_service.sesion.application.dto.CuentaLogin;
import com.marketplace.auth_service.sesion.application.port.out.CuentasLoginPort;
import com.marketplace.auth_service.sesion.domain.exception.DependenciaNoDisponibleException;

import java.util.Optional;
import java.util.UUID;

/**
 * Marcador hasta que Eva entregue {@code CuentasPort}: la resolución y el lock
 * de cuenta son suyos. Falla en voz alta en vez de inventar cuentas; el puente
 * real lo reemplaza en composición (T007) sin cambiar el coordinador.
 */
public class CuentasLoginPendiente implements CuentasLoginPort {

    @Override
    public Optional<CuentaLogin> buscarPorCorreo(String correoNormalizado) {
        throw new DependenciaNoDisponibleException("CuentasPort de Eva");
    }

    @Override
    public Optional<CuentaLogin> bloquear(UUID usuarioId) {
        throw new DependenciaNoDisponibleException("CuentasPort de Eva");
    }
}
