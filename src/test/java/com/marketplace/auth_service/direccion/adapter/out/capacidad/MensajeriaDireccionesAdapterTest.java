package com.marketplace.auth_service.direccion.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El evento lleva el nombre del campo, nunca los valores de la dirección (SPEC-16, catálogo de eventos). */
class MensajeriaDireccionesAdapterTest {

    @Test
    void guardaUnEventoDeAtributosActualizadosSoloConElNombreDelCampo() {
        List<EventoUsuario> guardados = new ArrayList<>();
        MensajeriaPort mensajeria = new MensajeriaPort() {
            @Override public UUID guardarEvento(EventoUsuario evento) { guardados.add(evento); return evento.eventoId(); }
            @Override public UUID guardarCorreo(EntregaCorreo correo) { throw new AssertionError("no envía correos"); }
        };
        UUID usuarioId = UUID.randomUUID();

        new MensajeriaDireccionesAdapter(mensajeria).direccionesActualizadas(usuarioId);

        assertThat(guardados).hasSize(1);
        EventoUsuario e = guardados.get(0);
        assertThat(e.topico()).isEqualTo("usuario.atributos_actualizados");
        assertThat(e.carga()).containsEntry("usuarioId", usuarioId.toString()).containsEntry("campos", "direcciones");
        assertThat(e.carga().keySet()).containsExactlyInAnyOrder("usuarioId", "campos", "fecha");
    }
}
