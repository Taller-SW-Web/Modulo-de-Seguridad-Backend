package com.marketplace.auth_service.autorizacion.adapter.out.persistence;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.UsuarioRolJpaEntity;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.mapper.AutorizacionPersistenceMapper;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.CatalogoJpaRepository;
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
    private final CatalogoJpaRepository catalogo;

    public AutorizacionJpaAdapter(UsuarioRolJpaRepository usuarioRol, RolPermisoJpaRepository rolPermiso,
                                  CatalogoJpaRepository catalogo) {
        this.usuarioRol = usuarioRol;
        this.rolPermiso = rolPermiso;
        this.catalogo = catalogo;
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

    @Override
    public Set<UUID> usuariosConRol(Rol rol) {
        return Set.copyOf(usuarioRol.usuariosConRol(rol));
    }

    @Override
    public void bloquearFilaAdministrador() {
        if (catalogo.bloquearFilaAdministrador().isEmpty()) {
            throw new IllegalStateException("La tabla rol no contiene ADMIN_SISTEMA: el catálogo no está sembrado");
        }
    }

    @Override
    public boolean asignar(UUID usuarioId, Rol rol) {
        if (usuarioRol.existsById(new UsuarioRolJpaEntity.Id(usuarioId, rol))) {
            return false;
        }
        usuarioRol.saveAndFlush(new UsuarioRolJpaEntity(usuarioId, rol));
        return true;
    }

    @Override
    public boolean quitar(UUID usuarioId, Rol rol) {
        UsuarioRolJpaEntity.Id id = new UsuarioRolJpaEntity.Id(usuarioId, rol);
        if (!usuarioRol.existsById(id)) {
            return false;
        }
        usuarioRol.deleteById(id);
        usuarioRol.flush();
        return true;
    }
}
