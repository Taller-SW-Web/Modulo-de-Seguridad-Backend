package com.marketplace.auth_service.auditoria.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El sobre público de {@code usuario.atributos_actualizados} lleva {@code campos} como lista JSON. */
class SobreAtributosActualizadosTest {

    private static Map<String, Object> sobre(String campos) {
        UUID id = UUID.randomUUID();
        return MensajeriaService.sobre(new OutboxPort.Mensaje(id, "usuario.atributos_actualizados",
                Map.of("eventoId", id.toString(), "usuarioId", UUID.randomUUID().toString(),
                        "campos", campos, "fecha", "2026-10-23T00:00:00Z"), 0));
    }

    @SuppressWarnings("unchecked")
    private static Object campos(Map<String, Object> sobre) {
        return ((Map<String, Object>) sobre.get("datos")).get("campos");
    }

    @Test
    void unCampoSePublicaComoListaDeUnElemento() {
        Map<String, Object> sobre = sobre("direcciones");

        assertThat(sobre.get("tipo")).isEqualTo("usuario.atributos_actualizados");
        assertThat(sobre.get("version")).isEqualTo(1);
        assertThat(campos(sobre)).isEqualTo(List.of("direcciones"));
    }

    @Test
    void variosCamposSeSeparanEnLista() {
        assertThat(campos(sobre("nombres,apellidos"))).isEqualTo(List.of("nombres", "apellidos"));
    }

    @Test
    void sinCamposEsUnaListaVaciaNoUnTexto() {
        assertThat(campos(sobre(""))).isEqualTo(List.of());
    }
}
