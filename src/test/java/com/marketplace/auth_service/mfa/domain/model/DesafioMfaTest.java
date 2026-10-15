package com.marketplace.auth_service.mfa.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesafioMfaTest {
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final UUID ID = UUID.randomUUID(), USUARIO = UUID.randomUUID(), CREDENCIAL = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);

    private static DesafioMfa nuevo() {
        return DesafioMfa.nuevo(ID, USUARIO, CREDENCIAL, 3, HASH, AHORA);
    }

    @Test
    void dura300SegundosYElLimiteExactoEsInvalido() {
        var desafio = nuevo();
        assertThat(desafio.expiraEn()).isEqualTo(AHORA.plusSeconds(300));
        assertThat(desafio.vigenteEn(AHORA)).isTrue();
        assertThat(desafio.vigenteEn(AHORA.plusSeconds(299))).isTrue();
        assertThat(desafio.vigenteEn(AHORA.plusSeconds(300))).isFalse();
        assertThat(desafio.vigenteEn(AHORA.minusNanos(1))).isFalse();
    }

    @Test
    void vinculaUsuarioCredencialYVersion() {
        assertThat(nuevo().correspondeA(USUARIO, CREDENCIAL, 3)).isTrue();
        assertThat(nuevo().correspondeA(UUID.randomUUID(), CREDENCIAL, 3)).isFalse();
        assertThat(nuevo().correspondeA(USUARIO, UUID.randomUUID(), 3)).isFalse();
        assertThat(nuevo().correspondeA(USUARIO, CREDENCIAL, 4)).isFalse();
    }

    @Test
    void historicoSinRevisionNuncaAutorizaLogin() {
        var historico = new DesafioMfa(ID, USUARIO, null, null, HASH, AHORA, AHORA.plusSeconds(300), false);
        assertThat(historico.vigenteEn(AHORA)).isFalse();
        assertThat(historico.correspondeA(USUARIO, CREDENCIAL, 3)).isFalse();
    }

    @Test
    void consumidoNoEstaVigente() {
        var consumido = new DesafioMfa(ID, USUARIO, CREDENCIAL, 3L, HASH, AHORA, AHORA.plusSeconds(300), true);
        assertThat(consumido.vigenteEn(AHORA)).isFalse();
    }

    @Test
    void rechazaRevisionIncompletaYTiemposInvalidos() {
        assertThatThrownBy(() -> new DesafioMfa(ID, USUARIO, CREDENCIAL, null, HASH, AHORA, AHORA.plusSeconds(300), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DesafioMfa.nuevo(ID, USUARIO, CREDENCIAL, -1, HASH, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DesafioMfa(ID, USUARIO, CREDENCIAL, 3L, HASH, AHORA, AHORA, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noAceptaTokenEnLugarDeHashNiLoExponeEnToString() {
        assertThatThrownBy(() -> DesafioMfa.nuevo(ID, USUARIO, CREDENCIAL, 3, "token-en-claro", AHORA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("token-en-claro");
        assertThat(nuevo().toString()).doesNotContain(HASH);
    }
}
