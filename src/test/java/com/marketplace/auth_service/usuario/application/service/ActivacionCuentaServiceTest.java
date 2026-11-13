package com.marketplace.auth_service.usuario.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoActivacion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoRegistro;
import com.marketplace.auth_service.usuario.application.port.out.CuentasPersonalPort;
import com.marketplace.auth_service.usuario.application.port.out.EfectosActivacionPort;
import com.marketplace.auth_service.usuario.application.port.out.TokensActivacionPort;
import com.marketplace.auth_service.usuario.domain.model.CuentaPersonalNueva;
import com.marketplace.auth_service.usuario.domain.model.TokenVerificacion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** FEAT-13-T1 (SPEC-03 RF-03.3 a RF-03.5): orden de la activación y qué queda al rechazarla. */
class ActivacionCuentaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-12T15:00:00Z");
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.7", "prueba", UUID.randomUUID());
    private static final UUID CUENTA = UUID.randomUUID();

    private final List<String> pasos = new ArrayList<>();
    private TokenVerificacion token = new TokenVerificacion(UUID.randomUUID(), CUENTA, AHORA.plusSeconds(3600), false);
    private boolean sinActivar = true;
    private boolean consumible = true;
    private List<ResultadoRegistro.ReglaIncumplida> reglas = List.of();
    private boolean revertida;

    private final TokensActivacionPort tokens = new TokensActivacionPort() {
        @Override public Optional<TokenVerificacion> localizar(String tokenClaro) {
            return "conocido".equals(tokenClaro) ? Optional.of(token) : Optional.empty();
        }
        @Override public boolean consumir(UUID tokenId, UUID usuarioId, Instant ahora) {
            pasos.add("consumir");
            return consumible;
        }
    };

    private final CuentasPersonalPort cuentas = new CuentasPersonalPort() {
        @Override public boolean crear(CuentaPersonalNueva cuenta) { throw new AssertionError(); }
        @Override public boolean bloquearSinActivar(UUID usuarioId) { pasos.add("bloquear"); return sinActivar; }
        @Override public void activar(UUID usuarioId, String versionTerminos, Instant ahora) {
            pasos.add("activar:" + versionTerminos);
        }
    };

    private final EfectosActivacionPort efectos = new EfectosActivacionPort() {
        @Override public <T> T enTransaccion(Supplier<T> trabajo, Predicate<T> confirmar) {
            pasos.add("tx");
            T resultado = trabajo.get();
            revertida = !confirmar.test(resultado);
            return resultado;
        }
        @Override public List<ResultadoRegistro.ReglaIncumplida> validarContrasena(UUID usuarioId, String contrasena) {
            pasos.add("politica");
            return reglas;
        }
        @Override public void crearCredencial(UUID usuarioId, String contrasena) { pasos.add("credencial"); }
        @Override public void registrarActivacion(UUID usuarioId, ContextoPeticion c, Instant a) { pasos.add("activada"); }
        @Override public void auditarFallo(UUID usuarioId, String motivo, ContextoPeticion c, Instant a) {
            pasos.add("fallo:" + motivo);
        }
    };

    private final ActivacionCuentaService servicio = new ActivacionCuentaService(tokens, cuentas, efectos, "2026-11-v1",
            Clock.fixed(AHORA, ZoneOffset.UTC));

    private ResultadoActivacion activar() {
        return servicio.activar("conocido", "Activa#2026x", CONTEXTO);
    }

    @Test
    void activaConCredencialTerminosTokenConsumidoAuditoriaYEvento() {
        assertThat(activar()).isEqualTo(ResultadoActivacion.Estado.ACTIVADA);
        assertThat(pasos).containsExactly("tx", "bloquear", "politica", "credencial", "activar:2026-11-v1",
                "consumir", "activada");
        assertThat(revertida).isFalse();
    }

    @Test
    void contrasenaFueraDeLaPoliticaRevierteYElEnlaceSigueSirviendo() {
        reglas = List.of(new ResultadoRegistro.ReglaIncumplida("LONGITUD_MINIMA", 12));
        assertThat(activar()).isEqualTo(new ResultadoActivacion.PoliticaIncumplida(reglas));
        assertThat(pasos).containsExactly("tx", "bloquear", "politica", "fallo:POLITICA_INCUMPLIDA");
        assertThat(revertida).isTrue();
    }

    @Test
    void enlaceVencidoNoTocaLaCuenta() {
        token = new TokenVerificacion(token.id(), CUENTA, AHORA, false);
        assertThat(activar()).isEqualTo(ResultadoActivacion.Estado.EXPIRADA);
        assertThat(pasos).containsExactly("tx", "fallo:ENLACE_EXPIRADO");
    }

    @Test
    void enlaceUsadoOCuentaYaActivadaEs410YaUsado() {
        token = new TokenVerificacion(token.id(), CUENTA, AHORA.plusSeconds(60), true);
        assertThat(activar()).isEqualTo(ResultadoActivacion.Estado.YA_USADA);
        token = new TokenVerificacion(token.id(), CUENTA, AHORA.plusSeconds(60), false);
        sinActivar = false;
        pasos.clear();
        assertThat(activar()).isEqualTo(ResultadoActivacion.Estado.YA_USADA);
        assertThat(pasos).containsExactly("tx", "bloquear", "fallo:ENLACE_YA_USADO");
    }

    @Test
    void otraActivacionGanoElTokenYTodoSeRevierte() {
        consumible = false;
        assertThat(activar()).isEqualTo(ResultadoActivacion.Estado.YA_USADA);
        assertThat(revertida).isTrue();
        assertThat(pasos).doesNotContain("activada");
    }

    @Test
    void tokenDesconocidoNoAbreTransaccionNiAudita() {
        assertThat(servicio.activar("otro", "Activa#2026x", CONTEXTO)).isEqualTo(ResultadoActivacion.Estado.INVALIDA);
        assertThat(pasos).isEmpty();
    }
}
