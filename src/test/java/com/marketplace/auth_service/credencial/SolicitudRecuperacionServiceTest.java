package com.marketplace.auth_service.credencial;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.credencial.application.dto.CuentaRecuperacion;
import com.marketplace.auth_service.credencial.application.dto.ResultadoSolicitudRecuperacion;
import com.marketplace.auth_service.credencial.application.port.out.AuditoriaRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.CorreoRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.CuentasRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.CuotaRecuperacionPort;
import com.marketplace.auth_service.credencial.application.service.SolicitudRecuperacionService;
import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SolicitudRecuperacionServiceTest {
    private static final Instant AHORA = Instant.parse("2026-10-21T12:00:00Z");
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("127.0.0.1", "prueba",
            UUID.fromString("25a2fac0-3c38-4f9e-96a2-7cc7f2bec9a2"));
    private static final UUID USUARIO = UUID.fromString("09ed09a5-a7f3-48d3-96d5-c1018d012201");

    @Test
    void cuentaExistenteEmiteCorreoAuditaSinExponerCorreoYNormalizaLaEntrada() {
        var fixture = new Fixture(Optional.of(new CuentaRecuperacion(USUARIO, "persona@example.test", true)), Duration.ZERO);

        var resultado = fixture.service.solicitar("  Persona@Example.Test ", CONTEXTO);

        assertThat(resultado).isInstanceOf(ResultadoSolicitudRecuperacion.Aceptada.class);
        assertThat(fixture.cuota.correo).isEqualTo("persona@example.test");
        assertThat(fixture.correos).containsExactly(USUARIO);
        assertThat(fixture.auditoria.objetivos).containsExactly(USUARIO);
        assertThat(fixture.auditoria.aceptadas).containsExactly(true);
    }

    @Test
    void cuentaConBloqueoEfectivoTambienPuedeSolicitarRecuperacion() {
        var fixture = new Fixture(Optional.of(new CuentaRecuperacion(USUARIO, "bloqueada@example.test", true)), Duration.ZERO);

        assertThat(fixture.service.solicitar("bloqueada@example.test", CONTEXTO))
                .isInstanceOf(ResultadoSolicitudRecuperacion.Aceptada.class);
        assertThat(fixture.correos).containsExactly(USUARIO);
    }

    @Test
    void falloDeEnvioSeRespondeComoAceptadaYConservaLaReserva() {
        var fixture = new Fixture(Optional.of(new CuentaRecuperacion(USUARIO, "persona@example.test", true)),
                Duration.ZERO, (cuenta, ahora) -> { throw new com.marketplace.auth_service.credencial.domain.exception
                        .SolicitudRecuperacionNoDisponibleException(); });

        assertThat(fixture.service.solicitar("persona@example.test", CONTEXTO))
                .isInstanceOf(ResultadoSolicitudRecuperacion.Aceptada.class);
        assertThat(fixture.auditoria.aceptadas).containsExactly(true);
    }

    @Test
    void correoSinCuentaRecibeElMismoResultadoAceptadoYNoGeneraTokenNiCorreo() {
        var fixture = new Fixture(Optional.empty(), Duration.ZERO);

        var resultado = fixture.service.solicitar("nadie@example.test", CONTEXTO);

        assertThat(resultado).isInstanceOf(ResultadoSolicitudRecuperacion.Aceptada.class);
        assertThat(fixture.correos).isEmpty();
        assertThat(fixture.auditoria.objetivos).containsExactly((UUID) null);
        assertThat(fixture.auditoria.aceptadas).containsExactly(true);
    }

    @Test
    void cuentaNoElegibleNoRecibeCorreoPeroLaRespuestaSigueSiendoAceptada() {
        var fixture = new Fixture(Optional.of(new CuentaRecuperacion(USUARIO, "pendiente@example.test", false)), Duration.ZERO);

        assertThat(fixture.service.solicitar("pendiente@example.test", CONTEXTO))
                .isInstanceOf(ResultadoSolicitudRecuperacion.Aceptada.class);
        assertThat(fixture.correos).isEmpty();
    }

    @Test
    void cuartaSolicitudNoBuscaCuentaNiEmiteCorreoYDevuelveRetryAfter() {
        var fixture = new Fixture(Optional.of(new CuentaRecuperacion(USUARIO, "persona@example.test", true)),
                Duration.ofSeconds(37));

        var resultado = fixture.service.solicitar("persona@example.test", CONTEXTO);

        assertThat(resultado).isEqualTo(new ResultadoSolicitudRecuperacion.Limite(37));
        assertThat(fixture.consultasCuenta.get()).isZero();
        assertThat(fixture.correos).isEmpty();
        assertThat(fixture.auditoria.aceptadas).containsExactly(false);
    }

    private static final class Fixture {
        final AtomicInteger consultasCuenta = new AtomicInteger();
        final ArrayList<UUID> correos = new ArrayList<>();
        final Cuota cuota;
        final Registro auditoria = new Registro();
        final SolicitudRecuperacionService service;

        Fixture(Optional<CuentaRecuperacion> cuenta, Duration espera) {
            this(cuenta, espera, null);
        }

        Fixture(Optional<CuentaRecuperacion> cuenta, Duration espera, CorreoRecuperacionPort entregaForzado) {
            cuota = new Cuota(espera);
            CuentasRecuperacionPort cuentas = correo -> {
                consultasCuenta.incrementAndGet();
                return cuenta;
            };
            CorreoRecuperacionPort entrega = entregaForzado == null
                    ? (c, instante) -> correos.add(c.usuarioId()) : entregaForzado;
            service = new SolicitudRecuperacionService(cuentas, cuota, entrega, auditoria,
                    Clock.fixed(AHORA, ZoneOffset.UTC));
        }
    }

    private static final class Cuota implements CuotaRecuperacionPort {
        private final Duration espera;
        private String correo;
        Cuota(Duration espera) { this.espera = espera; }
        @Override public Duration reservar(String normalizado, Instant ahora) { correo = normalizado; return espera; }
    }

    private static final class Registro implements AuditoriaRecuperacionPort {
        private final ArrayList<UUID> objetivos = new ArrayList<>();
        private final ArrayList<Boolean> aceptadas = new ArrayList<>();
        @Override public void registrar(UUID usuarioId, boolean aceptada, ContextoPeticion contexto, Instant ahora) {
            objetivos.add(usuarioId);
            aceptadas.add(aceptada);
        }
    }
}
