package com.marketplace.auth_service.mfa.adapter.out.persistence;

import com.marketplace.auth_service.mfa.adapter.out.persistence.mapper.DesafioMfaPersistenceMapper;
import com.marketplace.auth_service.mfa.adapter.out.persistence.mapper.OtpPersistenceMapper;
import com.marketplace.auth_service.mfa.adapter.out.persistence.repository.DesafioMfaJpaRepository;
import com.marketplace.auth_service.mfa.adapter.out.persistence.repository.OtpJpaRepository;
import com.marketplace.auth_service.mfa.application.port.out.PersistenciaMfaPort;
import com.marketplace.auth_service.mfa.domain.model.ContextoOtp;
import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import com.marketplace.auth_service.mfa.domain.model.Otp;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MfaJpaAdapter implements PersistenciaMfaPort {
    private final DesafioMfaJpaRepository desafios;
    private final OtpJpaRepository codigos;

    public MfaJpaAdapter(DesafioMfaJpaRepository desafios, OtpJpaRepository codigos) {
        this.desafios = desafios;
        this.codigos = codigos;
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void guardarDesafio(DesafioMfa desafio) {
        desafios.saveAndFlush(DesafioMfaPersistenceMapper.aEntidad(desafio));
    }

    @Override public Optional<DesafioMfa> buscarDesafioPorHash(String hash) {
        return desafios.findByHashToken(hash).map(DesafioMfaPersistenceMapper::aDominio);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Optional<DesafioMfa> bloquearDesafio(UUID id) {
        return desafios.bloquearPorId(id).map(DesafioMfaPersistenceMapper::aDominio);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void guardarOtp(Otp otp) {
        codigos.saveAndFlush(OtpPersistenceMapper.aEntidad(otp));
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Otp> bloquearOtp(UUID id) {
        return codigos.bloquearPorId(id).flatMap(OtpPersistenceMapper::aDominio);
    }

    @Override public List<Otp> listarCodigosDelDesafio(UUID desafioId) {
        return codigos.findByDesafioIdAndMotivoOrderByCreadoEnDescIdDesc(desafioId, ContextoOtp.PROPOSITO)
                .stream().flatMap(fila -> OtpPersistenceMapper.aDominio(fila).stream()).toList();
    }

    @Override public long contarSolicitudes(UUID usuarioId, Instant limiteExclusivo) {
        return codigos.countByUsuarioIdAndCreadoEnGreaterThan(usuarioId, limiteExclusivo.atOffset(ZoneOffset.UTC));
    }

    @Override public Optional<Instant> primeraSolicitudEnVentana(UUID usuarioId, Instant limiteExclusivo) {
        return codigos.findFirstByUsuarioIdAndCreadoEnGreaterThanOrderByCreadoEnAscIdAsc(
                usuarioId, limiteExclusivo.atOffset(ZoneOffset.UTC)).map(fila -> fila.getCreadoEn().toInstant());
    }
}
