package com.marketplace.auth_service.usuario.application.port.out;

import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioEstadoEfectivoPort {
    Optional<CuentaSnapshot> findEstadoEfectivoById(UUID usuarioId);
    PaginaResultado<CuentaSnapshot> findByFiltroWithEstadoEfectivo(FiltroCuenta filtro, Paginacion paginacion);
}