package com.marketplace.auth_service.mfa.adapter.out.persistence.mapper;

import com.marketplace.auth_service.mfa.adapter.out.persistence.entity.DesafioMfaJpaEntity;
import com.marketplace.auth_service.mfa.adapter.out.persistence.entity.OtpJpaEntity;
import com.marketplace.auth_service.mfa.domain.model.CanalOtp;
import com.marketplace.auth_service.mfa.domain.model.ContextoOtp;
import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import com.marketplace.auth_service.mfa.domain.model.Otp;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaPersistenceMapperTest {
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final UUID USUARIO = UUID.randomUUID(), CREDENCIAL = UUID.randomUUID(), DESAFIO = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);

    @Test
    void desafioConservaIdsRevisionHashYEstadoEnUtc() {
        var modelo = DesafioMfa.nuevo(DESAFIO, USUARIO, CREDENCIAL, 7, HASH, AHORA);
        var fila = DesafioMfaPersistenceMapper.aEntidad(modelo);
        assertThat(fila.getCreadoEn().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(fila.getExpiracion().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(DesafioMfaPersistenceMapper.aDominio(fila)).isEqualTo(modelo);
        assertThat(fila.toString()).doesNotContain(HASH);
    }

    @Test
    void horarioDeOtraZonaSeConvierteAlMismoInstante() {
        var fila = new DesafioMfaJpaEntity(DESAFIO, USUARIO, CREDENCIAL, 7L, HASH,
                AHORA.atOffset(ZoneOffset.ofHours(-5)), AHORA.plusSeconds(300).atOffset(ZoneOffset.ofHours(-5)), true);
        var modelo = DesafioMfaPersistenceMapper.aDominio(fila);
        assertThat(modelo.creadoEn()).isEqualTo(AHORA);
        assertThat(modelo.consumido()).isTrue();
    }

    @Test
    void leeHistoricoSinRevisionComoInvalidoPeroNoPermiteEscribirlo() {
        var fila = new DesafioMfaJpaEntity(DESAFIO, USUARIO, null, null, HASH,
                AHORA.atOffset(ZoneOffset.UTC), AHORA.plusSeconds(300).atOffset(ZoneOffset.UTC), false);
        var historico = DesafioMfaPersistenceMapper.aDominio(fila);
        assertThat(historico.vigenteEn(AHORA)).isFalse();
        assertThatThrownBy(() -> DesafioMfaPersistenceMapper.aEntidad(historico))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otpConservaContextoClaveCanalTiemposYEstado() {
        var contexto = new ContextoOtp(USUARIO, DESAFIO, UUID.randomUUID());
        var modelo = new Otp(contexto, HASH, "otp-test", CanalOtp.SMS, "+51900000000", AHORA,
                AHORA.plusSeconds(300), false, 2, AHORA.plusSeconds(10));
        var fila = OtpPersistenceMapper.aEntidad(modelo);
        assertThat(fila.getMotivo()).isEqualTo("LOGIN_MFA");
        assertThat(fila.getInvalidadoEn().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(OtpPersistenceMapper.aDominio(fila)).contains(modelo);
        assertThat(fila.toString()).doesNotContain(HASH, modelo.destino());
    }

    @Test
    void otpNuevoConservaInvalidadoNullYConsumo() {
        var modelo = Otp.nuevo(new ContextoOtp(USUARIO, DESAFIO, UUID.randomUUID()),
                HASH, "otp-test", CanalOtp.EMAIL, "c@ejemplo.test", AHORA);
        var fila = OtpPersistenceMapper.aEntidad(modelo);
        assertThat(fila.getInvalidadoEn()).isNull();
        assertThat(OtpPersistenceMapper.aDominio(fila)).contains(modelo);
        var usado = new Otp(modelo.contexto(), HASH, "otp-test", modelo.canal(), modelo.destino(),
                AHORA, AHORA.plusSeconds(300), true, 1, null);
        assertThat(OtpPersistenceMapper.aDominio(OtpPersistenceMapper.aEntidad(usado))).contains(usado);
    }

    @Test
    void otrosPropositosYSinContextoNoSeConviertenEnLogin() {
        for (String motivo : new String[]{"RECUPERACION", "VERIFICACION", "LOGIN_MFA"}) {
            var historico = new OtpJpaEntity(UUID.randomUUID(), USUARIO, null, HASH, null,
                    "c@ejemplo.test", CanalOtp.EMAIL, motivo, AHORA.atOffset(ZoneOffset.UTC),
                    AHORA.plusSeconds(300).atOffset(ZoneOffset.UTC), false, 0, null);
            assertThat(OtpPersistenceMapper.aDominio(historico)).isEmpty();
        }
        var otro = new OtpJpaEntity(UUID.randomUUID(), USUARIO, DESAFIO, HASH, "otp-test",
                "c@ejemplo.test", CanalOtp.EMAIL, "RECUPERACION", AHORA.atOffset(ZoneOffset.UTC),
                AHORA.plusSeconds(300).atOffset(ZoneOffset.UTC), false, 0, null);
        assertThat(OtpPersistenceMapper.aDominio(otro)).isEmpty();
    }
}
