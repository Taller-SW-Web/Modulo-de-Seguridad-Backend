package com.marketplace.auth_service.sesion.domain.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccesoSesionTest {
    private static final Instant AHORA = Instant.parse("2026-10-12T20:00:00Z");

    @Test
    void nuevoExpiraEn900Segundos() {
        var acceso = AccesoSesion.nuevo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AHORA);
        assertThat(acceso.expiraEn()).isEqualTo(AHORA.plusSeconds(900));
    }

    @Test
    void asociaSoloASuUsuarioFamiliaYVigencia() {
        var usuario = UUID.randomUUID();
        var familia = UUID.randomUUID();
        var acceso = AccesoSesion.nuevo(UUID.randomUUID(), usuario, familia, AHORA);
        assertThat(acceso.asociaA(usuario, familia, AHORA.plusSeconds(1))).isTrue();
        assertThat(acceso.asociaA(UUID.randomUUID(), familia, AHORA.plusSeconds(1))).isFalse();
        assertThat(acceso.asociaA(usuario, UUID.randomUUID(), AHORA.plusSeconds(1))).isFalse();
        assertThat(acceso.asociaA(usuario, familia, AHORA.plusSeconds(901))).isFalse();
    }

    @Test
    void expiraDespuesDeCreado() {
        assertThatThrownBy(() -> new AccesoSesion(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AHORA, AHORA)).isInstanceOf(IllegalArgumentException.class);
    }
}
