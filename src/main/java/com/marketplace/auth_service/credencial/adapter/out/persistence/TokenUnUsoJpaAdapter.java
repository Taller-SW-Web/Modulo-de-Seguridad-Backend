package com.marketplace.auth_service.credencial.adapter.out.persistence;

import com.marketplace.auth_service.credencial.adapter.out.persistence.mapper.TokenUnUsoPersistenceMapper;
import com.marketplace.auth_service.credencial.adapter.out.persistence.repository.TokenUnUsoJpaRepository;
import com.marketplace.auth_service.credencial.adapter.out.persistence.entity.PropositoTokenPersistido;
import com.marketplace.auth_service.credencial.application.port.out.TokenUnUsoRepositoryPort;
import com.marketplace.auth_service.credencial.domain.model.PropositoTokenUnUso;
import com.marketplace.auth_service.credencial.domain.model.TokenUnUso;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

public final class TokenUnUsoJpaAdapter implements TokenUnUsoRepositoryPort {
    private final TokenUnUsoJpaRepository repository;
    private final EntityManager entityManager;
    public TokenUnUsoJpaAdapter(TokenUnUsoJpaRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }
    @Override public void guardar(TokenUnUso token) { repository.saveAndFlush(TokenUnUsoPersistenceMapper.aEntidad(token)); }
    @Override public Optional<TokenUnUso> buscar(String hash, PropositoTokenUnUso proposito) {
        var persistido = TokenUnUsoPersistenceMapper.aPersistido(proposito);
        var resultado = proposito == PropositoTokenUnUso.DESBLOQUEO_AUTOMATICO
                ? repository.findByHashTokenAndPropositoAndContextoIdIsNotNull(hash, persistido)
                : repository.findByHashTokenAndProposito(hash, persistido);
        return resultado
                .map(e -> { entityManager.refresh(e); return e; })
                .map(TokenUnUsoPersistenceMapper::aDominio);
    }
    @Override public Optional<TokenUnUso> bloquear(UUID id) {
        return repository.bloquearPorId(id)
                .map(e -> { entityManager.refresh(e, LockModeType.PESSIMISTIC_WRITE); return e; })
                .filter(e -> e.getProposito() != PropositoTokenPersistido.DESBLOQUEO || e.getContextoId() != null)
                .map(TokenUnUsoPersistenceMapper::aDominio);
    }
    @Override public void invalidarPrevios(UUID usuarioId, PropositoTokenUnUso proposito, Instant ahora) {
        repository.invalidarVigentes(usuarioId, TokenUnUsoPersistenceMapper.aPersistido(proposito), ahora.atOffset(ZoneOffset.UTC));
    }
    @Override public boolean marcarUsado(UUID id) {
        if (repository.consumir(id) != 1) return false;
        repository.findById(id).ifPresent(entityManager::refresh);
        return true;
    }
}
