package com.marketplace.auth_service.perfil.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentoProtegidoTest {
    @Test
    void exigeLosCuatroDatosJuntos() {
        assertThatThrownBy(() -> new DocumentoProtegido(null, "c", "k", "h")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DocumentoProtegido(TipoDocumento.DNI, " ", "k", "h"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentoProtegido(TipoDocumento.DNI, "c", "", "h"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentoProtegido(TipoDocumento.DNI, "c", "k", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void toStringNoExponeElCifrado() {
        DocumentoProtegido documento = new DocumentoProtegido(TipoDocumento.DNI, "SECRETO-CIFRADO", "k1", "HMAC-X");
        assertThat(documento.toString()).doesNotContain("SECRETO-CIFRADO").doesNotContain("HMAC-X").doesNotContain("k1");
    }

    @Test
    void perfilClienteSinDocumentoNiFechaEsValido() {
        PerfilCliente perfil = new PerfilCliente(UUID.randomUUID(), null, null);
        assertThat(perfil.documento()).isEmpty();
        assertThat(perfil.fechaNacimiento()).isEmpty();
    }

    @Test
    void perfilClienteConservaLoQueSeLeDa() {
        DocumentoProtegido documento = new DocumentoProtegido(TipoDocumento.CE, "c", "k", "h");
        PerfilCliente perfil = new PerfilCliente(UUID.randomUUID(), LocalDate.of(2000, 1, 2), documento);
        assertThat(perfil.documento()).containsSame(documento);
        assertThat(perfil.fechaNacimiento()).contains(LocalDate.of(2000, 1, 2));
    }

    @Test
    void perfilVendedorNoInventaValoresAusentes() {
        PerfilVendedor perfil = new PerfilVendedor(UUID.randomUUID(), null, null, null);
        assertThat(perfil.codigoVendedor()).isEmpty();
        assertThat(perfil.tienda()).isEmpty();
        assertThat(perfil.fechaIngreso()).isEmpty();
    }

    @Test
    void perfilesExigenUsuario() {
        assertThatThrownBy(() -> new PerfilCliente(null, null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PerfilVendedor(null, null, null, null)).isInstanceOf(NullPointerException.class);
    }
}
