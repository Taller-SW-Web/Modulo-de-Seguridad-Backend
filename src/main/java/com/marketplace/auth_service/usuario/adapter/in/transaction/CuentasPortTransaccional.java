package com.marketplace.auth_service.usuario.adapter.in.transaction;

import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Transactional(readOnly = true)
public class CuentasPortTransaccional implements CuentasPort {
    private final CuentasPort delegado;
    public CuentasPortTransaccional(CuentasPort delegado) {
        this.delegado = delegado;
    }
    @Override
    public Optional<CuentaSnapshot> buscarPorCorreoNormalizado(String correo) {
        return delegado.buscarPorCorreoNormalizado(correo);
    }
    @Override
    public Optional<CuentaSnapshot> leer(UUID id) { return delegado.leer(id); }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CuentaSnapshot bloquear(UUID id) { return delegado.bloquear(id); }
    @Override
    public long contarActivos(Set<UUID> ids) { return delegado.contarActivos(ids); }
    @Override
    public Map<UUID, CuentaSnapshot> leerLote(Set<UUID> ids) { return delegado.leerLote(ids); }
    @Override
    public PaginaResultado<CuentaSnapshot> consultar(FiltroCuenta filtro, Paginacion paginacion) {
        return delegado.consultar(filtro, paginacion);
    }
}
