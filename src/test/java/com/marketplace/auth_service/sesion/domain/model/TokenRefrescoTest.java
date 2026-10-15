package com.marketplace.auth_service.sesion.domain.model;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenRefrescoTest {
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    void nuevoDuraSieteDiasYNoGuardaSecreto() {
        var token = TokenRefresco.nuevo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), HASH, AHORA);
        assertThat(token.expiracion()).isEqualTo(AHORA.plus(Duration.ofDays(7)));
        assertThat(token.revocado()).isFalse();
        assertThat(token.usado()).isFalse();
        assertThat(token.toString()).doesNotContain(HASH);
    }

    @Test
    void soloSha256Hexadecimal() {
        var jti = UUID.randomUUID();
        var usuario = UUID.randomUUID();
        var familia = UUID.randomUUID();
        assertThatThrownBy(() -> TokenRefresco.nuevo(jti, usuario, familia, "secreto-en-claro", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TokenRefresco.nuevo(jti, usuario, familia, "z".repeat(64), AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usadoORevocadoYaNoEsVigente() {
        var token = TokenRefresco.nuevo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), HASH, AHORA);
        assertThat(token.vigenteEn(AHORA.plusSeconds(1))).isTrue();
        assertThat(token.vigenteEn(AHORA.plus(Duration.ofDays(8)))).isFalse();
        assertThat(token.marcarUsado().vigenteEn(AHORA.plusSeconds(1))).isFalse();
        assertThat(token.revocar().vigenteEn(AHORA.plusSeconds(1))).isFalse();
    }
}
