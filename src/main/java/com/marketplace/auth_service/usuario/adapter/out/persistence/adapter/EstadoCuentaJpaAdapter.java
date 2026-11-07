package com.marketplace.auth_service.usuario.adapter.out.persistence.adapter;

import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.port.out.EstadoCuentaPort;
import com.marketplace.auth_service.usuario.domain.model.EstadoCuenta;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Cambia el estado base sin borrar nada (SPEC-04): identidad, correo y roles se conservan. */
@Transactional(propagation = Propagation.MANDATORY)
public class EstadoCuentaJpaAdapter implements EstadoCuentaPort {
    private final UsuarioJpaRepository usuarios;

    public EstadoCuentaJpaAdapter(UsuarioJpaRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public void cambiar(UUID usuarioId, EstadoCuenta estado, Instant ahora) {
        var cuenta = usuarios.findById(usuarioId)
                .orElseThrow(() -> new IllegalStateException("Cuenta inexistente al cambiar su estado"));
        cuenta.setEstado(estado);
        cuenta.setActualizadoEn(ahora.atOffset(ZoneOffset.UTC));
        usuarios.saveAndFlush(cuenta);
    }
}
