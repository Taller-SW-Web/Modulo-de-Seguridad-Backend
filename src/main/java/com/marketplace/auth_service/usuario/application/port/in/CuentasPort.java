package com.marketplace.auth_service.usuario.application.port.in;

import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CuentasPort {
    Optional<CuentaSnapshot> buscarPorCorreoNormalizado(String correoNormalizado);
    Optional<CuentaSnapshot> leer(UUID usuarioId);
    CuentaSnapshot bloquear(UUID usuarioId);
    long contarActivos(Set<UUID> usuarioIds);
    /**
     * Lectura por lote (SPEC-18 RF-18.2, FEAT-06-T2; acordada en el issue #73 de documentación).
     * Devuelve solo las cuentas encontradas: los no hallados los calcula el llamador por
     * diferencia, igual que el límite de 100 IDs. Conjunto vacío → mapa vacío. Sin orden garantizado.
     */
    Map<UUID, CuentaSnapshot> leerLote(Set<UUID> usuarioIds);
    PaginaResultado<CuentaSnapshot> consultar(FiltroCuenta filtro, Paginacion paginacion);
}