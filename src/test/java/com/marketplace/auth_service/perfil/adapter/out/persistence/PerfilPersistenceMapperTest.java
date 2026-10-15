package com.marketplace.auth_service.perfil.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.perfil.adapter.out.persistence.entity.PerfilClienteJpaEntity;
import com.marketplace.auth_service.perfil.adapter.out.persistence.entity.PerfilVendedorJpaEntity;
import com.marketplace.auth_service.perfil.adapter.out.persistence.mapper.PerfilPersistenceMapper;
import com.marketplace.auth_service.perfil.domain.model.TipoDocumento;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PerfilPersistenceMapperTest {

    private PerfilClienteJpaEntity cliente(UUID id) {
        PerfilClienteJpaEntity e = new PerfilClienteJpaEntity();
        e.setUsuarioId(id);
        return e;
    }

    @Test
    void clienteSinNadaRegistradoQuedaAusenteSinInventarValores() {
        var perfil = PerfilPersistenceMapper.aDominio(cliente(UUID.randomUUID()));

        assertThat(perfil.fechaNacimiento()).isEmpty();
        assertThat(perfil.documento()).isEmpty();
    }

    @Test
    void clienteConDocumentoLoEntregaProtegidoYCompleto() {
        UUID id = UUID.randomUUID();
        var e = cliente(id);
        e.setFechaNacimiento(LocalDate.of(1998, 5, 20));
        e.setTipoDocumento(TipoDocumento.DNI);
        e.setDocumentoCifrado("opaco-cifrado");
        e.setDocumentoKeyId("k1");
        e.setDocumentoHmac("ab".repeat(32));

        var perfil = PerfilPersistenceMapper.aDominio(e);

        assertThat(perfil.usuarioId()).isEqualTo(id);
        assertThat(perfil.fechaNacimiento()).contains(LocalDate.of(1998, 5, 20));
        var doc = perfil.documento().orElseThrow();
        assertThat(doc.tipo()).isEqualTo(TipoDocumento.DNI);
        assertThat(doc.cifrado()).isEqualTo("opaco-cifrado");
        assertThat(doc.keyId()).isEqualTo("k1");
        assertThat(doc.hmac()).isEqualTo("ab".repeat(32));
        assertThat(doc.toString()).doesNotContain("opaco-cifrado");
    }

    @Test
    void documentoAMediasNoSeDisfrazaDeAusente() {
        var e = cliente(UUID.randomUUID());
        e.setTipoDocumento(TipoDocumento.CE);

        assertThatThrownBy(() -> PerfilPersistenceMapper.aDominio(e))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void vendedorConservaNulosComoSinRegistrar() {
        UUID id = UUID.randomUUID();
        var e = new PerfilVendedorJpaEntity();
        e.setUsuarioId(id);
        e.setTienda("Tienda Centro");

        var perfil = PerfilPersistenceMapper.aDominio(e);

        assertThat(perfil.usuarioId()).isEqualTo(id);
        assertThat(perfil.tienda()).contains("Tienda Centro");
        assertThat(perfil.codigoVendedor()).isEmpty();
        assertThat(perfil.fechaIngreso()).isEmpty();
    }
}
