package com.marketplace.auth_service.direccion.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.auditoria.application.port.out.OutboxPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El evento lleva el nombre del campo, nunca los valores de la dirección (SPEC-16, catálogo de eventos). */
class OutboxDireccionesAdapterTest {

    @Test
    void guardaUnEventoDeAtributosActualizadosSoloConElNombreDelCampo() {
        List<OutboxPort.Mensaje> guardados = new ArrayList<>();
        OutboxPort outbox = new OutboxPort() {
            @Override public void guardar(Mensaje mensaje) { guardados.add(mensaje); }
            @Override public List<Mensaje> reclamarCorreos(int lote, Instant ahora, Instant hasta) { return List.of(); }
            @Override public List<Mensaje> reclamarEventos(int lote, Instant ahora, Instant hasta) { return List.of(); }
            @Override public void marcarEnviado(UUID id, Instant ahora) { }
            @Override public void marcarFallo(UUID id, String error, Instant proximo, boolean definitivo) { }
        };
        UUID usuarioId = UUID.randomUUID();

        new OutboxDireccionesAdapter(outbox).direccionesActualizadas(usuarioId);

        assertThat(guardados).hasSize(1);
        OutboxPort.Mensaje m = guardados.get(0);
        assertThat(m.topico()).isEqualTo("usuario.atributos_actualizados");
        assertThat(m.payload()).containsEntry("usuarioId", usuarioId.toString()).containsEntry("campos", "direcciones");
        assertThat(m.payload()).containsKeys("eventoId", "fecha");
        assertThat(m.payload().keySet()).containsExactlyInAnyOrder("eventoId", "usuarioId", "campos", "fecha");
        assertThat(m.id().toString()).isEqualTo(m.payload().get("eventoId"));
    }
}
