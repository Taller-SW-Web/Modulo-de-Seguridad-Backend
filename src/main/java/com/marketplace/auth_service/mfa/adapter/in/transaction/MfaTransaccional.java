package com.marketplace.auth_service.mfa.adapter.in.transaction;

import com.marketplace.auth_service.mfa.application.dto.*;
import com.marketplace.auth_service.mfa.application.port.in.MfaPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.marketplace.auth_service.shared.domain.Rol;
import java.util.UUID;

/**
 * Único MfaPort publicado por MfaConfig con los proveedores reales.
 * El coordinador conserva la transacción y el lock previo de cuenta.
 */
public class MfaTransaccional implements MfaPort {
    private final MfaPort delegado;
    public MfaTransaccional(MfaPort delegado) { this.delegado = Objects.requireNonNull(delegado); }

    @Override public boolean esEfectivo(boolean voluntario, Set<Rol> roles) {
        return delegado.esEfectivo(voluntario, roles);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public ResultadoCreacionDesafio crearDesafio(UUID usuarioId, RevisionCredencialMfa revision) {
        return delegado.crearDesafio(usuarioId, revision);
    }
    @Override @Transactional(readOnly = true)
    public Optional<EstadoMfa> leerEstado(UUID usuarioId, Set<Rol> rolesActuales) {
        return delegado.leerEstado(usuarioId, rolesActuales);
    }
}
