package com.marketplace.auth_service.sesion.domain.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SesionFamiliaTest {
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");

    @Test
    void nuevaFamiliaNaceActivaSinRevocadaEn() {
        var familia = SesionFamilia.nueva(UUID.randomUUID(), UUID.randomUUID(), AHORA);
        assertThat(familia.revocada()).isFalse();
        assertThat(familia.revocadaEn()).isNull();
    }

    @Test
    void revocarFijaFechaYNoReactiva() {
        var familia = SesionFamilia.nueva(UUID.randomUUID(), UUID.randomUUID(), AHORA);
        var revocada = familia.revocar(AHORA.plusSeconds(60));
        assertThat(revocada.revocada()).isTrue();
        assertThat(revocada.revocadaEn()).isEqualTo(AHORA.plusSeconds(60));
        assertThat(revocada.revocar(AHORA.plusSeconds(120))).isSameAs(revocada);
    }

    @Test
    void coherenciaRevocadaExigida() {
        var id = UUID.randomUUID();
        var usuario = UUID.randomUUID();
        assertThatThrownBy(() -> new SesionFamilia(id, usuario, true, AHORA, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SesionFamilia(id, usuario, false, AHORA, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SesionFamilia(id, usuario, true, AHORA, AHORA.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNoExponeDatos() {
        var familia = SesionFamilia.nueva(UUID.randomUUID(), UUID.randomUUID(), AHORA);
        assertThat(familia.toString()).doesNotContain("usuarioId");
    }
}
