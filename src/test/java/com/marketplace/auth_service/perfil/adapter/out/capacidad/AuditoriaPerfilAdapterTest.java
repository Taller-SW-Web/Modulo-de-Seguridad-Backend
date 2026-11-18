package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;

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

/** La edición propia y la administrativa dejan la misma auditoría; solo cambia {@code detalle.via}. */
class AuditoriaPerfilAdapterTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final class AuditoriaDoble implements AuditoriaPort {
        final List<RegistroSeguro> noCriticas = new ArrayList<>();
        boolean fallar;

        @Override
        public void registrarCritica(RegistroSeguro registro) {
            throw new AssertionError("ATRIBUTOS_ACTUALIZADOS es no crítica");
        }

        @Override
        public void registrarNoCriticaTrasCommit(RegistroSeguro registro) {
            if (fallar) {
                throw new IllegalStateException("caída simulada");
            }
            noCriticas.add(registro);
        }

        @Override
        public PaginaActividad actividadPropia(UUID titular, int pagina, int tamano) {
            throw new UnsupportedOperationException();
        }
    }

    private static ActorUsuario actor(UUID sub) {
        return new ActorUsuario(sub, UUID.randomUUID(), Set.of("CLIENTE"), Set.of());
    }

    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.7", "JUnit", UUID.randomUUID());

    @Test
    void edicionPropiaRegistraAlTitularComoActorYObjetivoConViaPropia() {
        AuditoriaDoble auditoria = new AuditoriaDoble();

        new AuditoriaPerfilAdapter(auditoria).atributosActualizadosPorTitular(actor(A), List.of("nombres", "celular"),
                CONTEXTO);

        assertThat(auditoria.noCriticas).hasSize(1);
        RegistroSeguro r = auditoria.noCriticas.get(0);
        assertThat(r.accion()).isEqualTo("ATRIBUTOS_ACTUALIZADOS");
        assertThat(r.actorId()).isEqualTo(A.toString());
        assertThat(r.objetivoUsuarioId()).isEqualTo(A);
        assertThat(r.ip()).isEqualTo("203.0.113.7");
        assertThat(r.detalle()).containsEntry("campos", "nombres,celular").containsEntry("via", "PROPIA");
    }

    @Test
    void edicionAdministrativaConservaViaAdministracion() {
        AuditoriaDoble auditoria = new AuditoriaDoble();

        new AuditoriaPerfilAdapter(auditoria).atributosActualizados(actor(A), B, List.of("tienda"), CONTEXTO);

        RegistroSeguro r = auditoria.noCriticas.get(0);
        assertThat(r.actorId()).isEqualTo(A.toString());
        assertThat(r.objetivoUsuarioId()).isEqualTo(B);
        assertThat(r.detalle()).containsEntry("via", "ADMINISTRACION");
    }

    @Test
    void unFalloDeAuditoriaNoSePropaga() {
        AuditoriaDoble auditoria = new AuditoriaDoble();
        auditoria.fallar = true;

        new AuditoriaPerfilAdapter(auditoria).atributosActualizadosPorTitular(actor(A), List.of("nombres"), null);

        assertThat(auditoria.noCriticas).isEmpty();
    }
}
