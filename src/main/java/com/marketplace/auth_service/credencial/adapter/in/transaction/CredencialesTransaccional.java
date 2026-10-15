package com.marketplace.auth_service.credencial.adapter.in.transaction;

import com.marketplace.auth_service.credencial.application.dto.*;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.shared.domain.Rol;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CredencialesTransaccional implements CredencialesPort {
    private final CredencialesPort delegado;
    public CredencialesTransaccional(CredencialesPort delegado) { this.delegado = delegado; }
    @Override @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public PreverificacionCredencial preverificar(Optional<UUID> usuarioId, SecretoEfimero secreto) {
        return delegado.preverificar(usuarioId, secreto);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public boolean confirmarRevision(RevisionCredencial revision) { return delegado.confirmarRevision(revision); }
    @Override @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public boolean evaluarCaducidad(UUID usuarioId, Set<Rol> roles, Instant instante) {
        return delegado.evaluarCaducidad(usuarioId, roles, instante);
    }
    @Override @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public ValidacionPassword validarNueva(UUID usuarioId, SecretoEfimero secreto) {
        return delegado.validarNueva(usuarioId, secreto);
    }
}
