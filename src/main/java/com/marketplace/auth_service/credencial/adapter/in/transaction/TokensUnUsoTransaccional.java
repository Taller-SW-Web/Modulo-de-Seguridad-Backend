package com.marketplace.auth_service.credencial.adapter.in.transaction;

import com.marketplace.auth_service.credencial.application.dto.PropositoToken;
import com.marketplace.auth_service.credencial.application.dto.TokenEmitido;
import com.marketplace.auth_service.credencial.application.dto.TokenUnUsoSnapshot;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.credencial.application.service.TokensUnUsoService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Asegura que el token participe en el commit/rollback del productor (Luis). */
public class TokensUnUsoTransaccional implements TokensUnUsoPort {
    private final TokensUnUsoService servicio;
    public TokensUnUsoTransaccional(TokensUnUsoService servicio) { this.servicio = servicio; }

    // Localiza antes de adquirir los locks del productor; dentro de él comparte su transacción.
    @Override @Transactional(readOnly = true)
    public Optional<TokenUnUsoSnapshot> localizar(String hash, PropositoToken proposito) {
        return servicio.localizar(hash, proposito);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public TokenEmitido emitir(UUID usuarioId, PropositoToken proposito, Duration ttl) {
        return servicio.emitir(usuarioId, proposito, ttl);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public TokenEmitido emitir(UUID usuarioId, PropositoToken proposito, UUID contextoId, Duration ttl) {
        return servicio.emitir(usuarioId, proposito, contextoId, ttl);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public boolean consumir(UUID id, UUID usuarioId, PropositoToken proposito, Instant ahora) {
        return servicio.consumir(id, usuarioId, proposito, ahora);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public boolean consumir(UUID id, UUID usuarioId, PropositoToken proposito, UUID contextoId, Instant ahora) {
        return servicio.consumir(id, usuarioId, proposito, contextoId, ahora);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void invalidarPrevios(UUID usuarioId, PropositoToken proposito) {
        servicio.invalidarPrevios(usuarioId, proposito);
    }
}
