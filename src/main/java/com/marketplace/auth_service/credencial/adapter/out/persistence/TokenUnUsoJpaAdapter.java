package com.marketplace.auth_service.credencial.adapter.out.persistence;

import com.marketplace.auth_service.credencial.adapter.out.persistence.mapper.TokenUnUsoPersistenceMapper;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.TokenUnUsoJpaRepository;
import com.marketplace.auth_service.credencial.application.port.out.TokenUnUsoRepositoryPort;
import com.marketplace.auth_service.credencial.domain.model.PropositoTokenUnUso;
import com.marketplace.auth_service.credencial.domain.model.TokenUnUso;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

public final class TokenUnUsoJpaAdapter implements TokenUnUsoRepositoryPort {
    private final TokenUnUsoJpaRepository repository;
    public TokenUnUsoJpaAdapter(TokenUnUsoJpaRepository repository) { this.repository = repository; }
    @Override public void guardar(TokenUnUso token) { repository.saveAndFlush(TokenUnUsoPersistenceMapper.aEntidad(token)); }
    @Override public Optional<TokenUnUso> buscar(String hash, PropositoTokenUnUso proposito) {
        return repository.findByHashTokenAndProposito(hash, TokenUnUsoPersistenceMapper.aPersistido(proposito))
                .map(TokenUnUsoPersistenceMapper::aDominio);
    }
    @Override public Optional<TokenUnUso> bloquear(UUID id) {
        return repository.bloquearPorId(id).map(TokenUnUsoPersistenceMapper::aDominio);
    }
    @Override public void invalidarPrevios(UUID usuarioId, PropositoTokenUnUso proposito, Instant ahora) {
        repository.invalidarVigentes(usuarioId, TokenUnUsoPersistenceMapper.aPersistido(proposito), ahora.atOffset(ZoneOffset.UTC));
    }
    @Override public void marcarUsado(UUID id) { repository.consumir(id); }
}
