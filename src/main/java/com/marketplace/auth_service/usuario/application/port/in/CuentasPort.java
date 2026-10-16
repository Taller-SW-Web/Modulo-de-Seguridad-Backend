package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CuentasPort {
    Optional<CuentaSnapshot> buscarPorCorreoNormalizado(String correoNormalizado);
    Optional<CuentaSnapshot> leer(UUID usuarioId);
    CuentaSnapshot bloquear(UUID usuarioId);
    long contarActivos(Set<UUID> usuarioIds);
    PaginaResultado<CuentaSnapshot> consultar(FiltroCuenta filtro, Paginacion paginacion);
}