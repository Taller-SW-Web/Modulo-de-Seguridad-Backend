package com.marketplace.auth_service.autorizacion.adapter.out.persistence;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.RolPermisoJpaEntity;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.CatalogoJpaRepository;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.PermisoJpaRepository;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.RolPermisoJpaRepository;
import com.marketplace.auth_service.autorizacion.application.dto.PermisoCatalogado;
import com.marketplace.auth_service.autorizacion.application.dto.RolCatalogado;
import com.marketplace.auth_service.autorizacion.application.port.out.CatalogoLecturaPort;
import com.marketplace.auth_service.shared.domain.Rol;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Catálogo sembrado leído con los repositorios de solo lectura del dueño. */
public class CatalogoLecturaJpaAdapter implements CatalogoLecturaPort {

    private final CatalogoJpaRepository roles;
    private final RolPermisoJpaRepository rolPermiso;
    private final PermisoJpaRepository permisos;

    public CatalogoLecturaJpaAdapter(CatalogoJpaRepository roles, RolPermisoJpaRepository rolPermiso,
                                     PermisoJpaRepository permisos) {
        this.roles = roles;
        this.rolPermiso = rolPermiso;
        this.permisos = permisos;
    }

    @Override
    public List<RolCatalogado> roles() {
        Map<Rol, List<String>> porRol = new EnumMap<>(Rol.class);
        for (RolPermisoJpaEntity fila : rolPermiso.findAll()) {
            porRol.computeIfAbsent(fila.getRol(), r -> new ArrayList<>()).add(fila.getPermisoCodigo());
        }
        return roles.findAll().stream()
                .map(r -> new RolCatalogado(r.getCodigo().name(), r.getNombre(), r.getDescripcion(),
                        porRol.getOrDefault(r.getCodigo(), List.of())))
                .toList();
    }

    @Override
    public List<PermisoCatalogado> permisos() {
        return permisos.findAll().stream()
                .map(p -> new PermisoCatalogado(p.getCodigo(), p.getModulo(), p.getDescripcion()))
                .toList();
    }
}
