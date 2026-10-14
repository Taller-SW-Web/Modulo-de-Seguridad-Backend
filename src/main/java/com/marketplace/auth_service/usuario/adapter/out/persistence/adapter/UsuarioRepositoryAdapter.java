package com.marketplace.auth_service.usuario.adapter.out.persistence.adapter;

import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioEstadoEfectivoJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.entity.UsuarioJpaEntity;
import com.marketplace.auth_service.usuario.adapter.out.persistence.mapper.UsuarioPersistenceMapper;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioEstadoEfectivoRepository;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.dto.CuentaSnapshot;
import com.marketplace.auth_service.usuario.application.dto.FiltroCuenta;
import com.marketplace.auth_service.usuario.application.dto.Paginacion;
import com.marketplace.auth_service.usuario.application.dto.PaginaResultado;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioEstadoEfectivoPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import com.marketplace.auth_service.usuario.domain.model.CodigoRol;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UsuarioRepositoryAdapter implements UsuarioRepositoryPort, UsuarioEstadoEfectivoPort {

    private final UsuarioJpaRepository usuarioJpaRepository;
    private final UsuarioEstadoEfectivoRepository usuarioEstadoEfectivoRepository;

    public UsuarioRepositoryAdapter(UsuarioJpaRepository usuarioJpaRepository,
                                     UsuarioEstadoEfectivoRepository usuarioEstadoEfectivoRepository) {
        this.usuarioJpaRepository = usuarioJpaRepository;
        this.usuarioEstadoEfectivoRepository = usuarioEstadoEfectivoRepository;
    }

    @Override
    public Optional<CuentaSnapshot> findByCorreoNormalizado(String correoNormalizado) {
        return usuarioJpaRepository.findByCorreoNormalizado(correoNormalizado)
                .map(e -> UsuarioPersistenceMapper.toSnapshot(e, UsuarioPersistenceMapper.extraerRoles(e)));
    }

    @Override
    public Optional<CuentaSnapshot> findById(UUID usuarioId) {
        return usuarioJpaRepository.findById(usuarioId)
                .map(e -> UsuarioPersistenceMapper.toSnapshot(e, UsuarioPersistenceMapper.extraerRoles(e)));
    }

    @Override
    public CuentaSnapshot lockById(UUID usuarioId) {
        return usuarioJpaRepository.lockById(usuarioId)
                .map(e -> UsuarioPersistenceMapper.toSnapshot(e, UsuarioPersistenceMapper.extraerRoles(e)))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
    }

    @Override
    public long countActiveByIds(Set<UUID> usuarioIds) {
        if (usuarioIds == null || usuarioIds.isEmpty()) return 0L;
        return usuarioJpaRepository.countActiveByIds(usuarioIds);
    }

    @Override
    public PaginaResultado<CuentaSnapshot> findByFiltro(FiltroCuenta filtro, Paginacion paginacion) {
        PageRequest pageRequest = PageRequest.of(paginacion.pagina() - 1, paginacion.tamano(), Sort.by("createdAt").descending());

        EstadoCuenta estado = filtro != null && filtro.estado() != null ? filtro.estado() : null;
        String correoFragmento = filtro != null ? filtro.correoFragmento() : null;

        Page<UsuarioJpaEntity> page = usuarioJpaRepository.findByFiltro(estado, correoFragmento, pageRequest);

        List<CuentaSnapshot> elementos = page.getContent().stream()
                .map(e -> UsuarioPersistenceMapper.toSnapshot(e, UsuarioPersistenceMapper.extraerRoles(e)))
                .collect(Collectors.toList());

        return new PaginaResultado<>(elementos, paginacion.pagina(), paginacion.tamano(), page.getTotalElements());
    }

    @Override
    public Optional<CuentaSnapshot> findEstadoEfectivoById(UUID usuarioId) {
        return usuarioEstadoEfectivoRepository.findById(usuarioId)
                .map(e -> {
                    Set<CodigoRol> roles = obtenerRolesDeUsuario(usuarioId);
                    return UsuarioPersistenceMapper.toSnapshotConEstadoEfectivo(e, roles);
                });
    }

    @Override
    public PaginaResultado<CuentaSnapshot> findByFiltroWithEstadoEfectivo(FiltroCuenta filtro, Paginacion paginacion) {
        PageRequest pageRequest = PageRequest.of(paginacion.pagina() - 1, paginacion.tamano(), Sort.by("createdAt").descending());

        EstadoCuenta estado = filtro != null && filtro.estado() != null ? filtro.estado() : null;
        String correoFragmento = filtro != null ? filtro.correoFragmento() : null;
        Set<UUID> rolIds = null;

        Page<UsuarioEstadoEfectivoJpaEntity> page = usuarioEstadoEfectivoRepository.findByFiltro(estado, correoFragmento, rolIds, pageRequest);

        List<CuentaSnapshot> elementos = page.getContent().stream()
                .map(e -> {
                    Set<CodigoRol> roles = obtenerRolesDeUsuario(e.getId());
                    return UsuarioPersistenceMapper.toSnapshotConEstadoEfectivo(e, roles);
                })
                .collect(Collectors.toList());

        return new PaginaResultado<>(elementos, paginacion.pagina(), paginacion.tamano(), page.getTotalElements());
    }

    private Set<CodigoRol> obtenerRolesDeUsuario(UUID usuarioId) {
        return Collections.emptySet();
    }
}