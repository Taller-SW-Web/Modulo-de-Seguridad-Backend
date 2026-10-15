package com.marketplace.auth_service.perfil.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DocumentoEnmascaradoTest {
    @Test
    void muestraCincoAsteriscosYLosUltimosTresDigitos() {
        assertThat(DocumentoEnmascarado.de("12345678")).isEqualTo("*****678");
        assertThat(DocumentoEnmascarado.de("00000234")).isEqualTo("*****234");
    }

    @Test
    void ignoraEspaciosDeLosBordes() {
        assertThat(DocumentoEnmascarado.de(" 12345678 ")).isEqualTo("*****678");
    }

    @Test
    void rechazaDocumentosMasCortosQueLoVisible() {
        assertThatThrownBy(() -> DocumentoEnmascarado.de("12")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaNulo() {
        assertThatThrownBy(() -> DocumentoEnmascarado.de(null)).isInstanceOf(NullPointerException.class);
    }
}
