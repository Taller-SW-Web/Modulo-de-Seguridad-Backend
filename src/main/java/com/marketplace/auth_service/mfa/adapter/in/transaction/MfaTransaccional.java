package com.marketplace.auth_service.mfa.adapter.in.transaction;

import com.marketplace.auth_service.mfa.application.dto.*;
import com.marketplace.auth_service.mfa.application.port.in.MfaPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Decorador para registrar explícitamente en el wiring cuando existan proveedores reales.
 * No se auto-registra un service con mocks ni se ignoran dependencias ausentes.
 */
public class MfaTransaccional implements MfaPort {
    private final MfaPort delegado;
    public MfaTransaccional(MfaPort delegado) { this.delegado = Objects.requireNonNull(delegado); }

    @Override public boolean esEfectivo(boolean voluntario, Set<String> roles) {
        return delegado.esEfectivo(voluntario, roles);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public ResultadoCreacionDesafio crearDesafio(UUID usuarioId, RevisionCredencialMfa revision) {
        return delegado.crearDesafio(usuarioId, revision);
    }
    @Override @Transactional(readOnly = true)
    public Optional<EstadoMfa> leerEstado(UUID usuarioId, Set<String> rolesActuales) {
        return delegado.leerEstado(usuarioId, rolesActuales);
    }
}
