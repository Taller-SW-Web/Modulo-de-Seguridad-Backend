package com.marketplace.auth_service.mfa.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpTest {
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final ContextoOtp CONTEXTO = new ContextoOtp(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private static final String HASH = "b".repeat(64), DESTINO = "privado@ejemplo.test";

    private static Otp estado(boolean usado, int intentos, Instant invalidado) {
        return new Otp(CONTEXTO, HASH, "otp-test", CanalOtp.EMAIL, DESTINO,
                AHORA, AHORA.plusSeconds(300), usado, intentos, invalidado);
    }

    @Test
    void nuevoOtpTiene300SegundosYSoloHash() {
        var otp = Otp.nuevo(CONTEXTO, HASH, "otp-test", CanalOtp.EMAIL, DESTINO, AHORA);
        assertThat(otp.expiraEn()).isEqualTo(AHORA.plusSeconds(300));
        assertThat(otp.intentos()).isZero();
        assertThat(otp.usado()).isFalse();
        assertThat(otp.invalidadoEn()).isNull();
    }

    @Test
    void vencimientoExactoYTiempoAnteriorNoSonVigentes() {
        assertThat(estado(false, 0, null).vigenteEn(AHORA)).isTrue();
        assertThat(estado(false, 0, null).vigenteEn(AHORA.plusSeconds(299))).isTrue();
        assertThat(estado(false, 0, null).vigenteEn(AHORA.plusSeconds(300))).isFalse();
        assertThat(estado(false, 0, null).vigenteEn(AHORA.minusNanos(1))).isFalse();
    }

    @Test
    void usadoInvalidadoOTresIntentosImpidenConsumo() {
        assertThat(estado(true, 0, null).vigenteEn(AHORA)).isFalse();
        assertThat(estado(false, 0, AHORA).vigenteEn(AHORA)).isFalse();
        assertThat(estado(false, 3, null).vigenteEn(AHORA)).isFalse();
        assertThat(estado(false, 2, null).vigenteEn(AHORA)).isTrue();
    }

    @Test
    void rechazaEstadosIncoherentes() {
        assertThatThrownBy(() -> estado(false, -1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> estado(false, 4, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> estado(false, 0, AHORA.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Otp.nuevo(CONTEXTO, HASH, " ", CanalOtp.EMAIL, DESTINO, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elContextoExigeUsuarioDesafioYOtp() {
        assertThatThrownBy(() -> new ContextoOtp(null, CONTEXTO.desafioId(), CONTEXTO.otpId()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ContextoOtp(CONTEXTO.usuarioId(), null, CONTEXTO.otpId()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ContextoOtp(CONTEXTO.usuarioId(), CONTEXTO.desafioId(), null))
                .isInstanceOf(NullPointerException.class);
        assertThat(ContextoOtp.PROPOSITO).isEqualTo("LOGIN_MFA");
    }

    @Test
    void nuncaAceptaUnCodigoEnClaroComoHashNiExponeDestino() {
        assertThatThrownBy(() -> Otp.nuevo(CONTEXTO, "123456", "otp-test", CanalOtp.EMAIL, DESTINO, AHORA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("123456");
        assertThat(estado(false, 0, null).toString()).doesNotContain(HASH, DESTINO);
    }
}
