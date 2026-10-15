package com.marketplace.auth_service.usuario.application.service;

import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.port.out.RolesConsultaPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ConsultaCuentasService implements CuentasPort {
    private final UsuarioRepositoryPort usuarios;
    private final RolesConsultaPort roles;
    public ConsultaCuentasService(UsuarioRepositoryPort usuarios, RolesConsultaPort roles) {
        this.usuarios = usuarios;
        this.roles = roles;
    }
    @Override
    public Optional<CuentaSnapshot> buscarPorCorreoNormalizado(String correo) {
        return usuarios.findByCorreoNormalizado(correo);
    }
    @Override
    public Optional<CuentaSnapshot> leer(UUID id) { return usuarios.findById(id); }
    @Override
    public CuentaSnapshot bloquear(UUID id) { return usuarios.lockById(id); }
    @Override
    public long contarActivos(Set<UUID> ids) { return usuarios.countActiveByIds(ids); }
    @Override
    public PaginaResultado<CuentaSnapshot> consultar(FiltroCuenta filtro, Paginacion paginacion) {
        Set<UUID> ids = filtro != null && filtro.rol() != null ? roles.usuariosConRol(filtro.rol()) : null;
        return usuarios.findByFiltro(filtro, paginacion, ids);
    }
}
