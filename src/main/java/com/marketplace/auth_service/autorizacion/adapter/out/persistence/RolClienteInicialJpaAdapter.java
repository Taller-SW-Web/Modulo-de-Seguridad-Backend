package com.marketplace.auth_service.autorizacion.adapter.out.persistence;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.UsuarioRolJpaEntity;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.port.in.RolClienteInicialInterna;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.MANDATORY)
public class RolClienteInicialJpaAdapter implements RolClienteInicialInterna {
    private final UsuarioRolJpaRepository usuarioRol;

    public RolClienteInicialJpaAdapter(UsuarioRolJpaRepository usuarioRol) {
        this.usuarioRol = usuarioRol;
    }

    @Override
    public void asignar(UUID usuarioId) {
        usuarioRol.saveAndFlush(new UsuarioRolJpaEntity(usuarioId, Rol.CLIENTE));
    }
}
