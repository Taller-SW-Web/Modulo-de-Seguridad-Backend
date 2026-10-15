package com.marketplace.auth_service.sesion.adapter.out.persistence;

import com.marketplace.auth_service.sesion.adapter.out.persistence.mapper.SesionPersistenceMapper;
import com.marketplace.auth_service.sesion.adapter.out.persistence.repository.AccesoSesionJpaRepository;
import com.marketplace.auth_service.sesion.adapter.out.persistence.repository.SesionFamiliaJpaRepository;
import com.marketplace.auth_service.sesion.adapter.out.persistence.repository.TokenRefrescoJpaRepository;
import com.marketplace.auth_service.sesion.application.port.out.PersistenciaSesionPort;
import com.marketplace.auth_service.sesion.domain.model.AccesoSesion;
import com.marketplace.auth_service.sesion.domain.model.SesionFamilia;
import com.marketplace.auth_service.sesion.domain.model.TokenRefresco;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SesionJpaAdapter implements PersistenciaSesionPort {
    private final SesionFamiliaJpaRepository familias;
    private final TokenRefrescoJpaRepository tokens;
    private final AccesoSesionJpaRepository accesos;

    public SesionJpaAdapter(SesionFamiliaJpaRepository familias, TokenRefrescoJpaRepository tokens,
                           AccesoSesionJpaRepository accesos) {
        this.familias = familias;
        this.tokens = tokens;
        this.accesos = accesos;
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void guardarFamilia(SesionFamilia familia) {
        familias.saveAndFlush(SesionPersistenceMapper.aEntidad(familia));
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SesionFamilia> bloquearFamilia(UUID familiaId) {
        return familias.bloquearPorId(familiaId).map(SesionPersistenceMapper::aDominio);
    }

    @Override public List<SesionFamilia> listarFamiliasDeUsuario(UUID usuarioId) {
        return familias.findByUsuarioIdOrderByIdAsc(usuarioId).stream()
                .map(SesionPersistenceMapper::aDominio).toList();
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void guardarToken(TokenRefresco token) {
        tokens.saveAndFlush(SesionPersistenceMapper.aEntidad(token));
    }

    @Override public Optional<TokenRefresco> buscarTokenPorHash(String hashToken) {
        return tokens.findByHashToken(hashToken).map(SesionPersistenceMapper::aDominio);
    }

    @Override public List<TokenRefresco> listarTokensDeFamilia(UUID familiaId) {
        return tokens.findByFamiliaIdOrderByJtiAsc(familiaId).stream()
                .map(SesionPersistenceMapper::aDominio).toList();
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void guardarAcceso(AccesoSesion acceso) {
        accesos.saveAndFlush(SesionPersistenceMapper.aEntidad(acceso));
    }

    @Override public Optional<AccesoSesion> buscarAccesoPorJti(UUID jti) {
        return accesos.findById(jti).map(SesionPersistenceMapper::aDominio);
    }
}
