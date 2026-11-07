package com.marketplace.auth_service.usuario.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.auth_service.shared.domain.ContextoPeticion;
import com.marketplace.auth_service.usuario.application.dto.ResultadoReenvio;
import com.marketplace.auth_service.usuario.application.dto.ResultadoVerificacion;
import com.marketplace.auth_service.usuario.application.port.out.CuentasVerificacionPort;
import com.marketplace.auth_service.usuario.application.port.out.EfectosVerificacionPort;
import com.marketplace.auth_service.usuario.application.port.out.TokensVerificacionPort;
import com.marketplace.auth_service.usuario.domain.exception.CuotaNoDisponibleException;
import com.marketplace.auth_service.usuario.domain.exception.VerificacionNoDisponibleException;
import com.marketplace.auth_service.usuario.domain.model.CanalOrigen;
import com.marketplace.auth_service.usuario.domain.model.CuentaPendiente;
import com.marketplace.auth_service.usuario.domain.model.TokenVerificacion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FEAT-01-T3 con dobles: los cuatro casos del token, reversión de rechazos y cuota neutra del reenvío. */
class VerificacionCorreoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-23T15:00:00Z");
    private static final ContextoPeticion CONTEXTO = new ContextoPeticion("203.0.113.7", "pruebas", UUID.randomUUID());
    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID TOKEN_ID = UUID.randomUUID();

    private final List<String> pasos = new ArrayList<>();
    private TokenVerificacion token;
    private boolean pendiente = true;
    private boolean consumible = true;
    private Duration espera = Duration.ZERO;
    private Optional<CuentaPendiente> cuentaPorCorreo = Optional.empty();
    private RuntimeException falloCorreo;
    private Boolean revertida;
    private VerificacionCorreoService servicio;

    @BeforeEach
    void preparar() {
        token = new TokenVerificacion(TOKEN_ID, USUARIO, AHORA.plus(Duration.ofHours(1)), false);
        TokensVerificacionPort tokens = new TokensVerificacionPort() {
            @Override public Optional<TokenVerificacion> localizar(String tokenClaro) {
                return "conocido".equals(tokenClaro) ? Optional.of(token) : Optional.empty();
            }
            @Override public boolean consumir(UUID tokenId, UUID usuarioId, Instant ahora) {
                pasos.add("consumir");
                return consumible;
            }
        };
        CuentasVerificacionPort cuentas = new CuentasVerificacionPort() {
            @Override public boolean activarSiPendiente(UUID usuarioId, Instant ahora) {
                pasos.add("activar");
                return pendiente;
            }
            @Override public Optional<CuentaPendiente> buscarPendiente(String correoNormalizado) {
                pasos.add("buscar:" + correoNormalizado);
                return cuentaPorCorreo;
            }
        };
        servicio = new VerificacionCorreoService(tokens, cuentas, (correo, ahora) -> {
            pasos.add("cuota:" + correo);
            return espera;
        }, (usuarioId, correo, nombres, canal) -> {
            pasos.add("enviar:" + correo + ":" + canal);
            if (falloCorreo != null) throw falloCorreo;
        }, new Efectos(), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void tokenVigenteActivaConsumePublicaYAuditaElExito() {
        assertThat(servicio.verificar("conocido", CONTEXTO)).isEqualTo(ResultadoVerificacion.VERIFICADA);

        assertThat(pasos).containsExactly("transaccion", "activar", "consumir", "usuario.creado", "auditoria:EXITO");
        assertThat(revertida).isFalse();
    }

    @Test
    void tokenVencidoNoActivaRevierteYAuditaElEnlaceExpirado() {
        token = new TokenVerificacion(TOKEN_ID, USUARIO, AHORA, false);

        assertThat(servicio.verificar("conocido", CONTEXTO)).isEqualTo(ResultadoVerificacion.EXPIRADA);
        assertThat(pasos).containsExactly("transaccion", "auditoria:ENLACE_EXPIRADO");
        assertThat(revertida).isTrue();
    }

    @Test
    void tokenUsadoONoConsumibleOCuentaYaActivaSeRespondenComoYaUsado() {
        token = new TokenVerificacion(TOKEN_ID, USUARIO, AHORA.plusSeconds(60), true);
        assertThat(servicio.verificar("conocido", CONTEXTO)).isEqualTo(ResultadoVerificacion.YA_USADA);

        token = new TokenVerificacion(TOKEN_ID, USUARIO, AHORA.plusSeconds(60), false);
        pendiente = false;
        assertThat(servicio.verificar("conocido", CONTEXTO)).isEqualTo(ResultadoVerificacion.YA_USADA);

        pendiente = true;
        consumible = false; // otra petición lo consumió entre la búsqueda y el bloqueo
        assertThat(servicio.verificar("conocido", CONTEXTO)).isEqualTo(ResultadoVerificacion.YA_USADA);

        assertThat(pasos).doesNotContain("usuario.creado").filteredOn(p -> p.startsWith("auditoria"))
                .containsOnly("auditoria:ENLACE_YA_USADO").hasSize(3);
        assertThat(revertida).isTrue();
    }

    @Test
    void tokenDesconocidoEsInvalidoSinTransaccionNiAuditoria() {
        assertThat(servicio.verificar("inventado", CONTEXTO)).isEqualTo(ResultadoVerificacion.INVALIDA);
        assertThat(pasos).isEmpty();
    }

    @Test
    void reenvioConCuotaYCuentaPendienteEmiteUnEnlaceNuevo() {
        cuentaPorCorreo = Optional.of(new CuentaPendiente(USUARIO, "maria@ejemplo.com", "María", CanalOrigen.MARKETPLACE));

        assertThat(servicio.reenviar("  Maria@Ejemplo.COM ", CONTEXTO)).isEqualTo(ResultadoReenvio.Aceptado.ACEPTADO);
        assertThat(pasos).containsExactly("transaccion", "cuota:maria@ejemplo.com", "transaccion",
                "buscar:maria@ejemplo.com", "enviar:maria@ejemplo.com:MARKETPLACE");
    }

    @Test
    void reenvioSinCuentaRespondeIgualYConsumeCuota() {
        assertThat(servicio.reenviar("nadie@ejemplo.com", CONTEXTO)).isEqualTo(ResultadoReenvio.Aceptado.ACEPTADO);
        assertThat(pasos).containsExactly("transaccion", "cuota:nadie@ejemplo.com", "transaccion", "buscar:nadie@ejemplo.com");
    }

    @Test
    void cuotaAgotadaNoBuscaNiEnviaYRedondeaLaEsperaHaciaArriba() {
        espera = Duration.ofMillis(1500);
        cuentaPorCorreo = Optional.of(new CuentaPendiente(USUARIO, "maria@ejemplo.com", "María", CanalOrigen.MARKETPLACE));

        assertThat(servicio.reenviar("maria@ejemplo.com", CONTEXTO)).isEqualTo(new ResultadoReenvio.Limitado(2));
        assertThat(pasos).containsExactly("transaccion", "cuota:maria@ejemplo.com");
    }

    @Test
    void colaCaidaEnElReenvioRespondeIgualParaNoDelatarLaCuenta() {
        cuentaPorCorreo = Optional.of(new CuentaPendiente(USUARIO, "maria@ejemplo.com", "María", CanalOrigen.MARKETPLACE));
        falloCorreo = new VerificacionNoDisponibleException(new IllegalStateException("outbox"));

        assertThat(servicio.reenviar("maria@ejemplo.com", CONTEXTO)).isEqualTo(ResultadoReenvio.Aceptado.ACEPTADO);
        // La cuota se confirmó antes, en su propia transacción; solo se revierte el envío.
        assertThat(pasos).containsExactly("transaccion", "cuota:maria@ejemplo.com", "transaccion",
                "buscar:maria@ejemplo.com", "enviar:maria@ejemplo.com:MARKETPLACE");
        assertThat(revertida).isTrue();
    }

    @Test
    void sinClaveDeCuotaFallaParaCualquierCorreo() {
        servicio = new VerificacionCorreoService(new TokensVerificacionPort() {
            @Override public Optional<TokenVerificacion> localizar(String tokenClaro) { return Optional.empty(); }
            @Override public boolean consumir(UUID tokenId, UUID usuarioId, Instant ahora) { return false; }
        }, new CuentasVerificacionPort() {
            @Override public boolean activarSiPendiente(UUID usuarioId, Instant ahora) { return false; }
            @Override public Optional<CuentaPendiente> buscarPendiente(String correo) { return Optional.empty(); }
        }, (correo, ahora) -> { throw new CuotaNoDisponibleException(); },
                (usuarioId, correo, nombres, canal) -> { }, new Efectos(), Clock.fixed(AHORA, ZoneOffset.UTC));

        assertThatThrownBy(() -> servicio.reenviar("nadie@ejemplo.com", CONTEXTO))
                .isInstanceOf(CuotaNoDisponibleException.class);
    }

    private final class Efectos implements EfectosVerificacionPort {
        @Override
        public <T> T enTransaccion(Supplier<T> trabajo, Predicate<T> confirmar) {
            pasos.add("transaccion");
            try {
                T resultado = trabajo.get();
                revertida = !confirmar.test(resultado);
                return resultado;
            } catch (RuntimeException fallo) {
                revertida = true; // como TransactionTemplate: una excepción revierte
                throw fallo;
            }
        }

        @Override
        public void publicarUsuarioCreado(UUID usuarioId, Instant ahora) {
            assertThat(usuarioId).isEqualTo(USUARIO);
            pasos.add("usuario.creado");
        }

        @Override
        public void auditarVerificacion(UUID usuarioId, String motivo, ContextoPeticion contexto) {
            assertThat(usuarioId).isEqualTo(USUARIO);
            pasos.add("auditoria:" + (motivo == null ? "EXITO" : motivo));
        }
    }
}
