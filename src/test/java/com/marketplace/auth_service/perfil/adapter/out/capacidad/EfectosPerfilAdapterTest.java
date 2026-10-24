package com.marketplace.auth_service.perfil.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.auditoria.application.dto.EntregaCorreo;
import com.marketplace.auth_service.auditoria.application.dto.EventoUsuario;
import com.marketplace.auth_service.auditoria.application.dto.PaginaActividad;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El puente traduce a los contratos de Christian y solo manda nombres de campo. */
class EfectosPerfilAdapterTest {
    private static final Instant AHORA = Instant.parse("2026-10-22T12:00:00Z");
    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-0000000000ad");

    private final List<EventoUsuario> eventos = new ArrayList<>();
    private final List<RegistroSeguro> registros = new ArrayList<>();
    private final List<String> orden = new ArrayList<>();

    private final MensajeriaPort mensajeria = new MensajeriaPort() {
        public UUID guardarEvento(EventoUsuario evento) { orden.add("evento"); eventos.add(evento); return evento.eventoId(); }
        public UUID guardarCorreo(EntregaCorreo correo) { throw new UnsupportedOperationException(); }
    };
    private final AuditoriaPort auditoria = new AuditoriaPort() {
        public void registrarCritica(RegistroSeguro r) { throw new UnsupportedOperationException("no es crítica"); }
        public void registrarNoCriticaTrasCommit(RegistroSeguro r) { orden.add("auditoria"); registros.add(r); }
        public PaginaActividad actividadPropia(UUID t, int p, int n) { throw new UnsupportedOperationException(); }
    };
    private final EfectosPerfilAdapter adaptador =
            new EfectosPerfilAdapter(auditoria, mensajeria, Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void guardaElEventoDelCatalogoConCamposSeparadosPorComaYSinValores() {
        adaptador.atributosActualizados(ANA, ANA, List.of("nombres", "celular"));

        EventoUsuario evento = eventos.get(0);
        assertThat(evento.topico()).isEqualTo("usuario.atributos_actualizados");
        assertThat(evento.carga()).containsEntry("usuarioId", ANA.toString())
                .containsEntry("campos", "nombres,celular").containsEntry("fecha", AHORA.toString())
                .hasSize(3);
    }

    @Test
    void auditaComoNoCriticaConActorYObjetivo() {
        adaptador.atributosActualizados(ADMIN, ANA, List.of("nombres"));

        RegistroSeguro r = registros.get(0);
        assertThat(r.accion()).isEqualTo("ATRIBUTOS_ACTUALIZADOS");
        assertThat(r.resultado()).isEqualTo("EXITO");
        assertThat(r.actorTipo()).isEqualTo("USUARIO");
        assertThat(r.actorId()).isEqualTo(ADMIN.toString());
        assertThat(r.objetivoUsuarioId()).isEqualTo(ANA);
        assertThat(r.detalle()).containsOnlyKeys("campos").containsEntry("campos", "nombres");
    }

    @Test
    void cadaEfectoTieneSuPropioIdentificadorParaDeduplicar() {
        adaptador.atributosActualizados(ANA, ANA, List.of("celular"));
        adaptador.atributosActualizados(ANA, ANA, List.of("celular"));

        assertThat(eventos.get(0).eventoId()).isNotEqualTo(eventos.get(1).eventoId());
        assertThat(registros.get(0).registroId()).isNotEqualTo(registros.get(1).registroId());
    }
}
