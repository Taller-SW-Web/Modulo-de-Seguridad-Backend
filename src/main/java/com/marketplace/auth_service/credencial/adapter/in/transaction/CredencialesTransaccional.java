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
    /**
     * Sin transacción propia: Argon2id es lento a propósito y no debe retener una conexión del
     * pool mientras calcula (con 50 logins concurrentes y 5 conexiones, el pool se agotaba). La
     * lectura de la credencial usa la transacción corta del repositorio; si el llamador ya tiene
     * una, se une a ella (EP-02-T007).
     */
    @Override @Transactional(propagation = Propagation.SUPPORTS)
    public PreverificacionCredencial preverificar(Optional<UUID> usuarioId, SecretoEfimero secreto) {
        return delegado.preverificar(usuarioId, secreto);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public boolean confirmarRevision(RevisionCredencial revision) { return delegado.confirmarRevision(revision); }
    @Override @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public boolean evaluarCaducidad(UUID usuarioId, Set<Rol> roles, Instant instante) {
        return delegado.evaluarCaducidad(usuarioId, roles, instante);
    }
    /** Igual que {@link #preverificar}: compara contra el historial con Argon2id sin retener conexión. */
    @Override @Transactional(propagation = Propagation.SUPPORTS)
    public ValidacionPassword validarNueva(UUID usuarioId, SecretoEfimero secreto) {
        return delegado.validarNueva(usuarioId, secreto);
    }
}
