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
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UsuarioRepositoryAdapter implements UsuarioRepositoryPort {
    private final UsuarioJpaRepository usuarios;
    private final UsuarioEstadoEfectivoRepository estados;
    public UsuarioRepositoryAdapter(UsuarioJpaRepository usuarios, UsuarioEstadoEfectivoRepository estados) {
        this.usuarios = usuarios;
        this.estados = estados;
    }
    private CuentaSnapshot snapshot(UsuarioJpaEntity usuario) {
        var estado = estados.findById(usuario.getId())
                .orElseThrow(() -> new IllegalStateException("Falta la proyección de una cuenta existente"));
        return UsuarioPersistenceMapper.toSnapshot(usuario, estado);
    }
    @Override
    public Optional<CuentaSnapshot> findByCorreoNormalizado(String correo) {
        return usuarios.findByCorreo(correo).map(this::snapshot);
    }
    @Override
    public Optional<CuentaSnapshot> findById(UUID id) { return usuarios.findById(id).map(this::snapshot); }
    @Override
    public CuentaSnapshot lockById(UUID id) {
        return snapshot(usuarios.lockById(id).orElseThrow(() -> new IllegalArgumentException("Cuenta inexistente")));
    }
    @Override
    public long countActiveByIds(Set<UUID> ids) {
        return ids.isEmpty() ? 0 : estados.countActiveByIds(ids);
    }
    @Override
    public PaginaResultado<CuentaSnapshot> findByFiltro(FiltroCuenta filtro, Paginacion paginacion, Set<UUID> idsPorRol) {
        if (idsPorRol != null && idsPorRol.isEmpty())
            return new PaginaResultado<>(List.of(), paginacion.pagina(), paginacion.tamano(), 0);
        Specification<UsuarioJpaEntity> condiciones = (usuario, consulta, cb) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (idsPorRol != null) filtros.add(usuario.get("id").in(idsPorRol));
            if (filtro != null && filtro.correoFragmento() != null && !filtro.correoFragmento().isEmpty()) {
                String fragmento = filtro.correoFragmento().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                filtros.add(cb.like(cb.lower(usuario.get("correo")), "%" + fragmento + "%", '\\'));
            }
            if (filtro != null && filtro.estado() != null) {
                var estado = consulta.subquery(UUID.class);
                var vista = estado.from(UsuarioEstadoEfectivoJpaEntity.class);
                estado.select(vista.get("id")).where(cb.equal(vista.get("id"), usuario.get("id")),
                        cb.equal(vista.get("estado"), EstadoCuenta.valueOf(filtro.estado())));
                filtros.add(cb.exists(estado));
            }
            return cb.and(filtros.toArray(Predicate[]::new));
        };
        if (paginacion.offset() > Integer.MAX_VALUE)
            return new PaginaResultado<>(List.of(), paginacion.pagina(), paginacion.tamano(), usuarios.count(condiciones));
        var pagina = usuarios.findAll(condiciones, PageRequest.of(paginacion.pagina() - 1, paginacion.tamano(),
                Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.asc("id"))));
        var ids = pagina.getContent().stream().map(UsuarioJpaEntity::getId).toList();
        Map<UUID, UsuarioEstadoEfectivoJpaEntity> porId = ids.isEmpty() ? Map.of() :
                estados.findAllById(ids).stream().collect(Collectors.toMap(UsuarioEstadoEfectivoJpaEntity::getId, Function.identity()));
        var elementos = pagina.getContent().stream().map(u -> UsuarioPersistenceMapper.toSnapshot(u, porId.get(u.getId()))).toList();
        return new PaginaResultado<>(elementos, paginacion.pagina(), paginacion.tamano(), pagina.getTotalElements());
    }
}
