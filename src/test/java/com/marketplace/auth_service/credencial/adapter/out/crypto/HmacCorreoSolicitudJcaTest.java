package com.marketplace.auth_service.credencial.adapter.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HmacCorreoSolicitudJcaTest {
    @Test
    void produceHmacHexDeterministaQueNoRevelaElCorreo() {
        var hmac = new HmacCorreoSolicitudJca("clave-de-prueba-de-32-bytes-minimo".getBytes(StandardCharsets.UTF_8));

        String primero = hmac.generar("persona@example.test");

        assertThat(primero).hasSize(64).matches("[0-9a-f]{64}").doesNotContain("persona", "example");
        assertThat(hmac.generar("persona@example.test")).isEqualTo(primero);
        assertThat(hmac.generar("otra@example.test")).isNotEqualTo(primero);
        assertThat(hmac.toString()).doesNotContain("clave-de-prueba");
    }
}
