package com.marketplace.auth_service.autorizacion.adapter.out.persistence;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.UsuarioRolJpaEntity;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.port.in.RolesPruebaInterna;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.MANDATORY)
public class RolesPruebaJpaAdapter implements RolesPruebaInterna {
    private final UsuarioRolJpaRepository usuarioRol;

    public RolesPruebaJpaAdapter(UsuarioRolJpaRepository usuarioRol) {
        this.usuarioRol = usuarioRol;
    }

    @Override
    public void asegurar(UUID usuarioId, Set<Rol> roles) {
        var actuales = Set.copyOf(usuarioRol.rolesDe(usuarioId));
        for (Rol rol : roles) {
            if (!actuales.contains(rol)) {
                usuarioRol.saveAndFlush(new UsuarioRolJpaEntity(usuarioId, rol));
            }
        }
    }
}
