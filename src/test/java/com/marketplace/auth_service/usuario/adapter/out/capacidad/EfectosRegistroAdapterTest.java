package com.marketplace.auth_service.usuario.adapter.out.capacidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.marketplace.auth_service.auditoria.application.dto.PaginaActividad;
import com.marketplace.auth_service.auditoria.application.dto.RegistroSeguro;
import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.domain.model.ActorTipo;
import com.marketplace.auth_service.auditoria.domain.model.RegistroAuditoria;
import com.marketplace.auth_service.auditoria.domain.model.Resultado;
import com.marketplace.auth_service.autorizacion.application.port.in.RolClienteInicialInterna;
import com.marketplace.auth_service.credencial.application.port.in.CredencialInicialInterna;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/** FEAT-01-T1 (RF-01.6): los rechazos se auditan sin identificar cuentas y con un detalle que SPEC-12 acepta. */
class EfectosRegistroAdapterTest {

    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.7", "pruebas", UUID.randomUUID());

    private final List<RegistroSeguro> noCriticos = new ArrayList<>();
    private final EfectosRegistroAdapter efectos = new EfectosRegistroAdapter(mock(CredencialesPort.class),
            mock(CredencialInicialInterna.class), mock(RolClienteInicialInterna.class), new AuditoriaPort() {
                @Override public void registrarCritica(RegistroSeguro registro) { throw new AssertionError("crítica"); }
                @Override public void registrarNoCriticaTrasCommit(RegistroSeguro registro) { noCriticos.add(registro); }
                @Override public PaginaActividad actividadPropia(UUID titular, int pagina, int tamano) {
                    throw new UnsupportedOperationException();
                }
            }, mock(PlatformTransactionManager.class), Clock.fixed(Instant.parse("2026-10-22T15:00:00Z"), ZoneOffset.UTC));

    /** Pasa el registro por el validador real de SPEC-12 (catálogo y detalle sin secretos). */
    private static RegistroAuditoria validado(RegistroSeguro r) {
        return RegistroAuditoria.nuevo(r.registroId(), r.accion(), Resultado.valueOf(r.resultado()),
                ActorTipo.valueOf(r.actorTipo()), r.actorId(), r.objetivoUsuarioId(), r.fecha(), r.ip(), r.agente(),
                r.detalle());
    }

    @Test
    void rechazoPorValidacionSoloGuardaNombresDeCampoYSinCanalInvalido() {
        efectos.auditarRechazo("VALIDACION", null, List.of("correo", "contrasena"), CONTEXTO);

        var registro = noCriticos.getFirst();
        validado(registro);
        assertThat(registro.accion()).isEqualTo("USUARIO_CREADO");
        assertThat(registro.resultado()).isEqualTo("FALLO");
        assertThat(registro.actorId()).isNull();
        assertThat(registro.objetivoUsuarioId()).isNull();
        assertThat(registro.detalle()).containsOnlyKeys("via", "motivo", "campos")
                .containsEntry("motivo", "VALIDACION").containsEntry("campos", "correo,contrasena");
    }

    @Test
    void correoNoDisponibleGuardaElCanalYNingunDatoDeLaCuenta() {
        efectos.auditarRechazo("CORREO_NO_DISPONIBLE", "MARKETPLACE", List.of(), CONTEXTO);

        var registro = noCriticos.getFirst();
        validado(registro);
        assertThat(registro.detalle()).containsOnlyKeys("via", "motivo", "canalOrigen")
                .containsEntry("canalOrigen", "MARKETPLACE");
        assertThat(registro.actorId()).isNull();
        assertThat(registro.objetivoUsuarioId()).isNull();
    }
}
