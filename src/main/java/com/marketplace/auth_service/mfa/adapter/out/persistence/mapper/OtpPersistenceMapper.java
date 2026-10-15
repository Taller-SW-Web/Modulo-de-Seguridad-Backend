package com.marketplace.auth_service.mfa.adapter.out.persistence.mapper;

import com.marketplace.auth_service.mfa.adapter.out.persistence.entity.OtpJpaEntity;
import com.marketplace.auth_service.mfa.domain.model.ContextoOtp;
import com.marketplace.auth_service.mfa.domain.model.Otp;
import java.time.ZoneOffset;
import java.util.Optional;

public final class OtpPersistenceMapper {
    private OtpPersistenceMapper() { }

    public static OtpJpaEntity aEntidad(Otp modelo) {
        return new OtpJpaEntity(modelo.contexto().otpId(), modelo.contexto().usuarioId(),
                modelo.contexto().desafioId(), modelo.hashCodigo(), modelo.hashKeyId(), modelo.destino(),
                modelo.canal(), ContextoOtp.PROPOSITO, modelo.creadoEn().atOffset(ZoneOffset.UTC),
                modelo.expiraEn().atOffset(ZoneOffset.UTC), modelo.usado(), modelo.intentos(),
                modelo.invalidadoEn() == null ? null : modelo.invalidadoEn().atOffset(ZoneOffset.UTC));
    }

    /** No convertir históricos incompletos ni OTP de otros propósitos en códigos de login. */
    public static Optional<Otp> aDominio(OtpJpaEntity fila) {
        if (!ContextoOtp.PROPOSITO.equals(fila.getMotivo()) || fila.getDesafioId() == null
                || fila.getHashKeyId() == null || fila.getHashKeyId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Otp(new ContextoOtp(fila.getUsuarioId(), fila.getDesafioId(), fila.getId()),
                fila.getHashCodigo(), fila.getHashKeyId(), fila.getCanal(), fila.getDestino(),
                fila.getCreadoEn().toInstant(), fila.getExpiraEn().toInstant(), fila.isUsado(),
                fila.getIntentos(), fila.getInvalidadoEn() == null ? null : fila.getInvalidadoEn().toInstant()));
    }
}
