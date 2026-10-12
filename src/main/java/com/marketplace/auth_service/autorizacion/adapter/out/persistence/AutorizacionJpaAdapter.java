package com.marketplace.auth_service.autorizacion.adapter.out.persistence;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.mapper.AutorizacionPersistenceMapper;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.RolPermisoJpaRepository;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.port.out.AsociacionesRolPort;
import com.marketplace.auth_service.autorizacion.domain.model.Rol;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Implementación JPA de {@link AsociacionesRolPort}. Solo toca tablas de autorización. */
public class AutorizacionJpaAdapter implements AsociacionesRolPort {

    private final UsuarioRolJpaRepository usuarioRol;
    private final RolPermisoJpaRepository rolPermiso;

    public AutorizacionJpaAdapter(UsuarioRolJpaRepository usuarioRol, RolPermisoJpaRepository rolPermiso) {
        this.usuarioRol = usuarioRol;
        this.rolPermiso = rolPermiso;
    }

    @Override
    public Set<Rol> rolesDe(UUID usuarioId) {
        return AutorizacionPersistenceMapper.aRoles(usuarioRol.rolesDe(usuarioId));
    }

    @Override
    public Set<String> permisosDe(UUID usuarioId) {
        return AutorizacionPersistenceMapper.aCodigos(usuarioRol.permisosDe(usuarioId));
    }

    @Override
    public Map<Rol, Set<String>> matrizPersistida() {
        return AutorizacionPersistenceMapper.aMatriz(rolPermiso.findAll());
    }
}
