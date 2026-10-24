package com.marketplace.auth_service.direccion.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.marketplace.auth_service.auditoria.application.dto.PaginaActividad;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.shared.domain.ActorUsuario;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ATRIBUTOS_ACTUALIZADOS no crítico: éxito sin valores de la dirección y fallo que no propaga (RF-16.10). */
class AuditoriaDireccionesAdapterTest {
    private final UUID actorId = UUID.randomUUID();
    private final UUID objetivo = UUID.randomUUID();
    private final ActorUsuario actor = new ActorUsuario(actorId, UUID.randomUUID(), Set.of(), Set.of());
    private final ContextoPeticion contexto = new ContextoPeticion("203.0.113.7", "JUnit", UUID.randomUUID());

    @Test
    void registraExitoNoCriticoConElNombreDelCampoYNingunValor() {
        List<RegistroSeguro> diferidas = new ArrayList<>();
        List<RegistroSeguro> criticas = new ArrayList<>();
        AuditoriaPort auditoria = new AuditoriaPort() {
            @Override public void registrarCritica(RegistroSeguro r) { criticas.add(r); }
            @Override public void registrarNoCriticaTrasCommit(RegistroSeguro r) { diferidas.add(r); }
            @Override public PaginaActividad actividadPropia(UUID titular, int pagina, int tamano) { return null; }
        };

        new AuditoriaDireccionesAdapter(auditoria).direccionAgregada(actor, objetivo, contexto);

        assertThat(criticas).as("no es crítica: no puede revertir el alta").isEmpty();
        assertThat(diferidas).singleElement().satisfies(r -> {
            assertThat(r.accion()).isEqualTo("ATRIBUTOS_ACTUALIZADOS");
            assertThat(r.resultado()).isEqualTo("EXITO");
            assertThat(r.actorTipo()).isEqualTo("USUARIO");
            assertThat(r.actorId()).isEqualTo(actorId.toString());
            assertThat(r.objetivoUsuarioId()).isEqualTo(objetivo);
            assertThat(r.ip()).isEqualTo("203.0.113.7");
            assertThat(r.agente()).isEqualTo("JUnit");
            assertThat(r.detalle()).containsOnlyKeys("campos").containsEntry("campos", "direcciones");
        });
    }

    @Test
    void siLaAuditoriaFallaElAltaNoSeVeAfectada() {
        AuditoriaPort rota = new AuditoriaPort() {
            @Override public void registrarCritica(RegistroSeguro r) { throw new IllegalStateException("x"); }
            @Override public void registrarNoCriticaTrasCommit(RegistroSeguro r) {
                throw new IllegalStateException("la cola de reintento no responde");
            }
            @Override public PaginaActividad actividadPropia(UUID titular, int pagina, int tamano) { return null; }
        };

        assertThatCode(() -> new AuditoriaDireccionesAdapter(rota).direccionAgregada(actor, objetivo, contexto))
                .doesNotThrowAnyException();
    }
}
