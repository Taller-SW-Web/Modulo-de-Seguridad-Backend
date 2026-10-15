package com.marketplace.auth_service.usuario.adapter.in.transaction;

import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.CuentaEscrituraAdapter;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentaEscrituraInterna;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import com.marketplace.auth_service.usuario.application.service.ConsultaCuentasService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class CuentasPortTransaccional implements CuentasPort {

    private final ConsultaCuentasService consultaService;
    private final CuentaEscrituraAdapter escrituraAdapter;
    private final UsuarioRepositoryPort usuarioRepositoryPort;

    public CuentasPortTransaccional(ConsultaCuentasService consultaService,
                                     CuentaEscrituraAdapter escrituraAdapter,
                                     UsuarioRepositoryPort usuarioRepositoryPort) {
        this.consultaService = consultaService;
        this.escrituraAdapter = escrituraAdapter;
        this.usuarioRepositoryPort = usuarioRepositoryPort;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public Optional<CuentaSnapshot> buscarPorCorreoNormalizado(String correoNormalizado) {
        return consultaService.buscarPorCorreoNormalizado(correoNormalizado);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public Optional<CuentaSnapshot> leer(UUID usuarioId) {
        return consultaService.leer(usuarioId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public CuentaSnapshot bloquear(UUID usuarioId) {
        return usuarioRepositoryPort.lockById(usuarioId);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public long contarActivos(Set<UUID> usuarioIds) {
        return consultaService.contarActivos(usuarioIds);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public PaginaResultado<CuentaSnapshot> consultar(FiltroCuenta filtro, Paginacion paginacion) {
        return consultaService.consultar(filtro, paginacion);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CuentaSnapshot actualizarBasicos(UUID usuarioId, com.marketplace.auth_service.usuario.application.port.in.CambioBasicos cambio) {
        return escrituraAdapter.actualizarBasicos(usuarioId, cambio);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void guardarContadores(UUID usuarioId, com.marketplace.auth_service.usuario.application.port.in.ContadoresProteccion contadores) {
        escrituraAdapter.guardarContadores(usuarioId, contadores);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void guardarMfaVoluntario(UUID usuarioId, boolean habilitado) {
        escrituraAdapter.guardarMfaVoluntario(usuarioId, habilitado);
    }
}