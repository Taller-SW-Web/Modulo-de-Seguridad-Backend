package com.marketplace.auth_service.perfil.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.perfil.application.dto.DocumentoProyectado;
import com.marketplace.auth_service.perfil.application.port.out.ProtectorDocumento;
import com.marketplace.auth_service.perfil.domain.model.DocumentoNoLegibleException;
import com.marketplace.auth_service.perfil.domain.model.DocumentoProtegido;
import com.marketplace.auth_service.perfil.domain.model.TipoDocumento;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProyectorDocumentoTest {
    private static final DocumentoProtegido CIFRADO =
            new DocumentoProtegido(TipoDocumento.DNI, "ZZZ-CIFRADO-999", "k1", "hmac");

    @Test
    void sinDocumentoDevuelveVacio() {
        ProyectorDocumento proyector = new ProyectorDocumento(documento -> {
            throw new AssertionError("no debe descifrar si no hay documento");
        });
        assertThat(proyector.proyectar(Optional.empty())).isEmpty();
    }

    @Test
    void enmascaraElNumeroDescifradoYNoElTextoCifrado() {
        ProtectorDocumento doble = documento -> "12345234";
        Optional<DocumentoProyectado> resultado = new ProyectorDocumento(doble).proyectar(Optional.of(CIFRADO));

        assertThat(resultado).contains(new DocumentoProyectado("DNI", "*****234"));
        assertThat(resultado.orElseThrow().toString()).doesNotContain("ZZZ-CIFRADO-999").doesNotContain("12345234");
    }

    @Test
    void siNoSePuedeLeerElFalloSePropagaYNoSeFingeAusencia() {
        ProtectorDocumento sinClave = documento -> {
            throw new DocumentoNoLegibleException("clave ausente");
        };
        ProyectorDocumento proyector = new ProyectorDocumento(sinClave);

        assertThatThrownBy(() -> proyector.proyectar(Optional.of(CIFRADO)))
                .isInstanceOf(DocumentoNoLegibleException.class);
    }
}
