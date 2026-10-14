package com.marketplace.auth_service.usuario.application.service;

import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioEstadoEfectivoPort;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ConsultaCuentasService implements CuentasPort {

    private final UsuarioRepositoryPort usuarioRepository;
    private final UsuarioEstadoEfectivoPort estadoEfectivoPort;

    public ConsultaCuentasService(UsuarioRepositoryPort usuarioRepository,
                                   UsuarioEstadoEfectivoPort estadoEfectivoPort) {
        this.usuarioRepository = usuarioRepository;
        this.estadoEfectivoPort = estadoEfectivoPort;
    }

    @Override
    public Optional<CuentaSnapshot> buscarPorCorreoNormalizado(String correoNormalizado) {
        return usuarioRepository.findByCorreoNormalizado(correoNormalizado);
    }

    @Override
    public Optional<CuentaSnapshot> leer(UUID usuarioId) {
        return estadoEfectivoPort.findEstadoEfectivoById(usuarioId);
    }

    @Override
    public CuentaSnapshot bloquear(UUID usuarioId) {
        return usuarioRepository.lockById(usuarioId);
    }

    @Override
    public long contarActivos(Set<UUID> usuarioIds) {
        return usuarioRepository.countActiveByIds(usuarioIds);
    }

    @Override
    public PaginaResultado<CuentaSnapshot> consultar(FiltroCuenta filtro, Paginacion paginacion) {
        return estadoEfectivoPort.findByFiltroWithEstadoEfectivo(filtro, paginacion);
    }
}