package com.marketplace.auth_service.usuario.adapter.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.usuario.domain.exception.CuotaNoDisponibleException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** FEAT-01-T3: la cuota guarda una huella estable del correo, nunca el correo ni la clave. */
class HuellaCorreoHmacTest {

    private static byte[] clave(int largo, int valor) {
        byte[] clave = new byte[largo];
        Arrays.fill(clave, (byte) valor);
        return clave;
    }

    @Test
    void huellaEstableHexadecimalSinElCorreoYDistintaPorClave() {
        var huellas = new HuellaCorreoHmac(clave(32, 7));
        String huella = huellas.generar("maria@ejemplo.com");

        assertThat(huella).matches("[0-9a-f]{64}").isEqualTo(huellas.generar("maria@ejemplo.com"))
                .doesNotContain("maria");
        assertThat(huellas.generar("otra@ejemplo.com")).isNotEqualTo(huella);
        assertThat(new HuellaCorreoHmac(clave(32, 8)).generar("maria@ejemplo.com")).isNotEqualTo(huella);
        assertThat(huellas.toString()).contains("REDACTADO");
    }

    @Test
    void claveCortaSeRechazaYSinClaveFallaCerrado() {
        assertThatThrownBy(() -> new HuellaCorreoHmac(clave(31, 7))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HuellaCorreoDenegada().generar("maria@ejemplo.com"))
                .isInstanceOf(CuotaNoDisponibleException.class);
    }
}
