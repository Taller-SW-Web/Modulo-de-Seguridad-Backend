package com.marketplace.auth_service.autorizacion.adapter.out.persistence;

import com.marketplace.auth_service.autorizacion.adapter.out.persistence.entity.UsuarioRolJpaEntity;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.port.in.RolInicialPersonalInterna;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.MANDATORY)
public class RolInicialPersonalJpaAdapter implements RolInicialPersonalInterna {
    private final UsuarioRolJpaRepository usuarioRol;

    public RolInicialPersonalJpaAdapter(UsuarioRolJpaRepository usuarioRol) {
        this.usuarioRol = usuarioRol;
    }

    @Override
    public void asignar(UUID usuarioId, Rol rol) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(rol, "rol");
        if (rol == Rol.CLIENTE) {
            throw new IllegalArgumentException("El alta administrativa no asigna CLIENTE");
        }
        usuarioRol.saveAndFlush(new UsuarioRolJpaEntity(usuarioId, rol));
    }
}
