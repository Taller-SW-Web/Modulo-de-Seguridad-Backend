package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UsuarioRepositoryPort {
    Optional<CuentaSnapshot> findByCorreoNormalizado(String correoNormalizado);
    Optional<CuentaSnapshot> findById(UUID usuarioId);
    CuentaSnapshot lockById(UUID usuarioId);
    long countActiveByIds(Set<UUID> usuarioIds);
    /** null: sin filtro de rol; conjunto vacío: ninguna coincidencia. */
    PaginaResultado<CuentaSnapshot> findByFiltro(FiltroCuenta filtro, Paginacion paginacion, Set<UUID> idsPorRol);
}
