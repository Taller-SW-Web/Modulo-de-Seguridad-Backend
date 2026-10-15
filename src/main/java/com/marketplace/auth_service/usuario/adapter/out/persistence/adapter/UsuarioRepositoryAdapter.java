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
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return usuarioJpaRepository.findByCorreo(correoNormalizado)
                .map(e -> UsuarioPersistenceMapper.toSnapshot(e, Collections.emptySet()));
    }

    @Override
    public Optional<CuentaSnapshot> findById(UUID usuarioId) {
        return usuarioJpaRepository.findById(usuarioId)
                .map(e -> UsuarioPersistenceMapper.toSnapshot(e, Collections.emptySet()));
    }

    @Override
    public CuentaSnapshot lockById(UUID usuarioId) {
        return usuarioJpaRepository.lockById(usuarioId)
                .map(e -> UsuarioPersistenceMapper.toSnapshot(e, Collections.emptySet()))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
    }

    @Override
    public long countActiveByIds(Set<UUID> usuarioIds) {
        if (usuarioIds == null || usuarioIds.isEmpty()) return 0L;
        return usuarioEstadoEfectivoRepository.countActiveByIds(usuarioIds);
    }

    @Override
    public PaginaResultado<CuentaSnapshot> findByFiltro(FiltroCuenta filtro, Paginacion paginacion) {
        PageRequest pageRequest = PageRequest.of(paginacion.pagina() - 1, paginacion.tamano(), Sort.by("creadoEn").descending());

        EstadoCuenta estado = null;
        if (filtro != null && filtro.estado() != null && !filtro.estado().isBlank()) {
            try {
                estado = EstadoCuenta.valueOf(filtro.estado().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        String correoFragmento = filtro != null ? filtro.correoFragmento() : null;

        Page<UsuarioJpaEntity> page = usuarioJpaRepository.findByFiltro(estado, correoFragmento, pageRequest);

        List<CuentaSnapshot> elementos = page.getContent().stream()
                .map(e -> UsuarioPersistenceMapper.toSnapshot(e, Collections.emptySet()))
                .collect(Collectors.toList());

        return new PaginaResultado<>(elementos, paginacion.pagina(), paginacion.tamano(), page.getTotalElements());
    }

    @Override
    public Optional<CuentaSnapshot> findEstadoEfectivoById(UUID usuarioId) {
        return usuarioJpaRepository.findById(usuarioId)
                .flatMap(usuario -> usuarioEstadoEfectivoRepository.findById(usuarioId)
                        .map(estadoEfectivo -> UsuarioPersistenceMapper.toSnapshotConEstadoEfectivo(usuario, estadoEfectivo, Collections.emptySet())));
    }

    @Override
    public PaginaResultado<CuentaSnapshot> findByFiltroWithEstadoEfectivo(FiltroCuenta filtro, Paginacion paginacion) {
        PageRequest pageRequest = PageRequest.of(paginacion.pagina() - 1, paginacion.tamano(), Sort.by("creadoEn").descending());

        EstadoCuenta estado = null;
        if (filtro != null && filtro.estado() != null && !filtro.estado().isBlank()) {
            try {
                estado = EstadoCuenta.valueOf(filtro.estado().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        String correoFragmento = filtro != null ? filtro.correoFragmento() : null;

        // Filtrar por estado en la vista, luego filtrar por correo en usuario
        Page<UsuarioEstadoEfectivoJpaEntity> page = usuarioEstadoEfectivoRepository.findByFiltro(estado, pageRequest);

        // Obtener IDs de la página
        Set<UUID> ids = page.getContent().stream()
                .map(UsuarioEstadoEfectivoJpaEntity::getId)
                .collect(Collectors.toSet());

        // Obtener usuarios completos de la tabla usuario
        Map<UUID, UsuarioJpaEntity> usuariosMap = new HashMap<>();
        if (!ids.isEmpty()) {
            usuarioJpaRepository.findAllById(ids).forEach(u -> usuariosMap.put(u.getId(), u));
        }

        // Filtrar por correo si se proporcionó
        List<CuentaSnapshot> elementos = page.getContent().stream()
                .map(e -> {
                    UsuarioJpaEntity usuario = usuariosMap.get(e.getId());
                    if (usuario == null) return null;
                    if (correoFragmento != null && !correoFragmento.isBlank()
                            && !usuario.getCorreo().toLowerCase().contains(correoFragmento.toLowerCase())) {
                        return null;
                    }
                    return UsuarioPersistenceMapper.toSnapshotConEstadoEfectivo(usuario, e, Collections.emptySet());
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        return new PaginaResultado<>(elementos, paginacion.pagina(), paginacion.tamano(), page.getTotalElements());
    }
}