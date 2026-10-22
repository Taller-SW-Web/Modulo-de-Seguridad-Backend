package com.marketplace.auth_service.usuario.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.dto.SolicitudRegistro;
import com.marketplace.auth_service.usuario.application.port.out.AltaCuentaPort;
import com.marketplace.auth_service.usuario.application.port.out.EfectosRegistroPort;
import com.marketplace.auth_service.usuario.domain.model.CanalOrigen;
import com.marketplace.auth_service.usuario.domain.model.CuentaNueva;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FEAT-01-T1 con dobles: orden de efectos, rechazos sin efectos dentro y auditoría de cada desenlace. */
class RegistroClienteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-22T15:00:00Z");
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.7", "pruebas", UUID.randomUUID());
    private static final String CLAVE = "Marketplace2026!";

    private final List<String> pasos = new ArrayList<>();
    private final List<CuentaNueva> creadas = new ArrayList<>();
    private boolean correoOcupado;
    private List<ResultadoRegistro.ReglaIncumplida> reglas = List.of();
    private Boolean revertida;
    private RegistroClienteService servicio;

    @BeforeEach
    void preparar() {
        AltaCuentaPort altas = cuenta -> {
            pasos.add("alta");
            if (correoOcupado) return false;
            creadas.add(cuenta);
            return true;
        };
        servicio = new RegistroClienteService(altas, new Efectos(), "1.0", Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static SolicitudRegistro solicitud(String canal) {
        return new SolicitudRegistro("  Maria@Ejemplo.COM ", " María ", "Quispe Rojas", "+51987654321", canal, CLAVE);
    }

    @Test
    void creaClientePendienteConTerminosYAuditaEnLaMismaTransaccion() {
        var resultado = servicio.registrar(solicitud("MARKETPLACE"), CONTEXTO);

        assertThat(resultado).isInstanceOf(ResultadoRegistro.Creada.class);
        var cuenta = creadas.getFirst();
        assertThat(((ResultadoRegistro.Creada) resultado).id()).isEqualTo(cuenta.id());
        assertThat(cuenta.correo()).isEqualTo("maria@ejemplo.com");
        assertThat(cuenta.nombres()).isEqualTo("María");
        assertThat(cuenta.canalOrigen()).isEqualTo(CanalOrigen.MARKETPLACE);
        assertThat(cuenta.versionTerminos()).isEqualTo("1.0");
        assertThat(cuenta.aceptadoEn()).isEqualTo(AHORA);
        assertThat(pasos).containsExactly("transaccion", "alta", "politica", "credencial", "rol",
                "auditoria:EXITO:MARKETPLACE");
        assertThat(revertida).isFalse();
    }

    @Test
    void correoOcupadoRevierteSinCredencialNiRolYAuditaElRechazo() {
        correoOcupado = true;

        var resultado = servicio.registrar(solicitud("WEB"), CONTEXTO);

        assertThat(resultado).isEqualTo(ResultadoRegistro.Rechazo.CORREO_NO_DISPONIBLE);
        assertThat(pasos).containsExactly("transaccion", "alta", "auditoria:FALLO:CORREO_NO_DISPONIBLE");
        assertThat(revertida).isTrue();
    }

    @Test
    void politicaIncumplidaRevierteLaCuentaYDevuelveLasReglas() {
        reglas = List.of(new ResultadoRegistro.ReglaIncumplida("LONGITUD_MINIMA", 10),
                new ResultadoRegistro.ReglaIncumplida("DATOS_PERSONALES", null));

        var resultado = servicio.registrar(solicitud("WEB"), CONTEXTO);

        assertThat(resultado).isEqualTo(new ResultadoRegistro.PoliticaIncumplida(reglas));
        assertThat(pasos).containsExactly("transaccion", "alta", "politica", "auditoria:FALLO:POLITICA_INCUMPLIDA");
        assertThat(revertida).isTrue();
    }

    @Test
    void solicitudInvalidaSoloAuditaLosNombresDeCampoSinAbrirElAlta() {
        servicio.auditarSolicitudInvalida(List.of("correo", "aceptaTerminos"), null, CONTEXTO);

        assertThat(pasos).containsExactly("auditoria:FALLO:VALIDACION:correo,aceptaTerminos");
        assertThat(creadas).isEmpty();
    }

    @Test
    void laSolicitudNoExponeLaContrasena() {
        assertThat(solicitud("WEB").toString()).doesNotContain(CLAVE, "maria");
    }

    private final class Efectos implements EfectosRegistroPort {
        @Override
        public ResultadoRegistro enTransaccion(Supplier<ResultadoRegistro> trabajo) {
            pasos.add("transaccion");
            var resultado = trabajo.get();
            revertida = !(resultado instanceof ResultadoRegistro.Creada);
            return resultado;
        }

        @Override
        public List<ResultadoRegistro.ReglaIncumplida> validarContrasena(UUID usuarioId, String contrasena) {
            assertThat(contrasena).isEqualTo(CLAVE);
            pasos.add("politica");
            return reglas;
        }

        @Override
        public void crearCredencial(UUID usuarioId, String contrasena) {
            pasos.add("credencial");
        }

        @Override
        public void asignarRolCliente(UUID usuarioId) {
            pasos.add("rol");
        }

        @Override
        public void auditarCreacion(UUID usuarioId, String canalOrigen, ContextoPeticion contexto) {
            pasos.add("auditoria:EXITO:" + canalOrigen);
        }

        @Override
        public void auditarRechazo(String motivo, String canalOrigen, List<String> campos, ContextoPeticion contexto) {
            pasos.add("auditoria:FALLO:" + motivo + (campos.isEmpty() ? "" : ":" + String.join(",", campos)));
        }
    }
}
