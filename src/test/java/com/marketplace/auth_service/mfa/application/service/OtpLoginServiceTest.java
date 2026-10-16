package com.marketplace.auth_service.mfa.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.auth_service.mfa.adapter.out.crypto.CriptografiaMfaJca;
import com.marketplace.auth_service.mfa.application.dto.CanalMfa;
import com.marketplace.auth_service.mfa.application.dto.ContactoMfa;
import com.marketplace.auth_service.mfa.application.dto.CuentaMfaSnapshot;
import com.marketplace.auth_service.mfa.application.dto.ResultadoSolicitudOtp;
import com.marketplace.auth_service.mfa.application.dto.ResultadoVerificacionOtp;
import com.marketplace.auth_service.mfa.application.port.out.CuentasMfaPort;
import com.marketplace.auth_service.mfa.application.port.out.EntregaOtpPort;
import com.marketplace.auth_service.mfa.application.port.out.PersistenciaMfaPort;
import com.marketplace.auth_service.mfa.domain.model.CanalOtp;
import com.marketplace.auth_service.mfa.domain.model.DesafioMfa;
import com.marketplace.auth_service.mfa.domain.model.Otp;
import com.marketplace.auth_service.shared.domain.Rol;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** EP-04-T003/T004 con criptografía real y persistencia en memoria (las reglas; PG va aparte). */
class OtpLoginServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID CREDENCIAL = UUID.randomUUID();
    private static final Instant INICIO = Instant.parse("2026-10-17T15:00:00Z");

    private final CriptografiaMfaJca crypto = new CriptografiaMfaJca("k1", Map.of("k1", new byte[32]), new SecureRandom());
    private Memoria memoria;
    private List<String> entregados;
    private List<String> eventos;
    private Instant ahora;
    private boolean disponible;
    private String celular;
    private OtpLoginService servicio;
    private String token;

    @BeforeEach
    void preparar() {
        memoria = new Memoria();
        entregados = new ArrayList<>();
        eventos = new ArrayList<>();
        ahora = INICIO;
        disponible = true;
        celular = null;
        Clock reloj = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return ahora; }
        };
        CuentasMfaPort cuentas = new CuentasMfaPort() {
            @Override public Optional<CuentaMfaSnapshot> leer(UUID id) {
                return Optional.of(new CuentaMfaSnapshot(id, disponible, true, false));
            }
            @Override public void bloquear(UUID id) { eventos.add("cuenta"); }
            @Override public Optional<ContactoMfa> contacto(UUID id) {
                return Optional.of(new ContactoMfa("admin@g7.test", celular));
            }
        };
        EntregaOtpPort entrega = (CanalOtp canal, String destino, String codigo) -> entregados.add(codigo);
        var validador = new ValidadorDesafioLogin(memoria, crypto, cuentas, u -> Set.of(Rol.ADMIN_SISTEMA),
                r -> true, reloj);
        servicio = new OtpLoginService(validador, cuentas, memoria, crypto, entrega, reloj, UUID::randomUUID);

        var generado = crypto.generarDesafio();
        token = generado.token();
        memoria.guardarDesafio(DesafioMfa.nuevo(UUID.randomUUID(), USUARIO, CREDENCIAL, 4, generado.hashToken(), INICIO));
    }

    private String codigoEnviado() {
        var r = (ResultadoSolicitudOtp.Enviado) servicio.solicitar(token, CanalMfa.EMAIL);
        assertThat(r.destinoEnmascarado()).isEqualTo("a***n@g7.test");
        return entregados.get(entregados.size() - 1);
    }

    @Test
    void solicitarGuardaSoloElHashYEntregaUnCodigoDeSeisDigitosConLockDeCuentaPrimero() {
        String codigo = codigoEnviado();

        assertThat(codigo).matches("[0-9]{6}");
        assertThat(memoria.otps.values()).singleElement().satisfies(o -> {
            assertThat(o.hashCodigo()).doesNotContain(codigo).hasSize(64);
            assertThat(o.expiraEn()).isEqualTo(INICIO.plusSeconds(300));
        });
        assertThat(eventos).first().isEqualTo("cuenta");
    }

    @Test
    void codigoCorrectoVerificaUnaSolaVezYConsumeElDesafio() {
        String codigo = codigoEnviado();

        var resultado = servicio.verificar(token, codigo);

        assertThat(resultado).isInstanceOf(ResultadoVerificacionOtp.Verificado.class);
        assertThat(((ResultadoVerificacionOtp.Verificado) resultado).revision().version()).isEqualTo(4);
        assertThat(servicio.verificar(token, codigo)).isEqualTo(ResultadoVerificacionOtp.Rechazo.DESAFIO_INVALIDO);
    }

    @Test
    void tresIntentosFallidosInvalidanElCodigoAunqueDespuesLlegueElCorrecto() {
        String codigo = codigoEnviado();
        String malo = codigo.equals("000000") ? "111111" : "000000";

        assertThat(servicio.verificar(token, malo)).isEqualTo(new ResultadoVerificacionOtp.CodigoInvalido(2));
        assertThat(servicio.verificar(token, malo)).isEqualTo(new ResultadoVerificacionOtp.CodigoInvalido(1));
        assertThat(servicio.verificar(token, malo)).isEqualTo(ResultadoVerificacionOtp.Rechazo.INTENTOS_AGOTADOS);
        assertThat(servicio.verificar(token, codigo)).isEqualTo(ResultadoVerificacionOtp.Rechazo.INTENTOS_AGOTADOS);
    }

    @Test
    void codigoVencidoEs410SinContarIntento() {
        String codigo = codigoEnviado();
        ahora = INICIO.plusSeconds(299);
        // el desafío también vence a los 300 s: se prueba con un código vencido dentro de un desafío vigente
        memoria.otps.replaceAll((id, o) -> new Otp(o.contexto(), o.hashCodigo(), o.hashKeyId(), o.canal(),
                o.destino(), o.creadoEn().minusSeconds(10), INICIO.plusSeconds(290), false, 0, null));

        assertThat(servicio.verificar(token, codigo)).isEqualTo(ResultadoVerificacionOtp.Rechazo.CODIGO_EXPIRADO);
        assertThat(memoria.otps.values()).singleElement().extracting(Otp::intentos).isEqualTo(0);
    }

    @Test
    void reenvioInvalidaElCodigoAnteriorYNoProrrogaElDesafio() {
        String primero = codigoEnviado();
        ahora = INICIO.plusSeconds(100);
        var segundo = (ResultadoSolicitudOtp.Enviado) servicio.solicitar(token, CanalMfa.EMAIL);
        String nuevo = entregados.get(1);

        assertThat(segundo.desafioExpiraEn()).isEqualTo(200);
        if (!primero.equals(nuevo)) {
            assertThat(servicio.verificar(token, primero)).isEqualTo(new ResultadoVerificacionOtp.CodigoInvalido(2));
        }
        assertThat(servicio.verificar(token, nuevo)).isInstanceOf(ResultadoVerificacionOtp.Verificado.class);
    }

    @Test
    void cuartaSolicitudEn15MinutosEs429ConEsperaPositivaSinEnviar() {
        codigoEnviado();
        codigoEnviado();
        codigoEnviado();
        ahora = INICIO.plusSeconds(60);

        var limite = servicio.solicitar(token, CanalMfa.EMAIL);

        assertThat(limite).isInstanceOf(ResultadoSolicitudOtp.Limite.class);
        assertThat(((ResultadoSolicitudOtp.Limite) limite).reintentarEn()).isBetween(1L, 15 * 60L);
        assertThat(entregados).hasSize(3);
    }

    @Test
    void desafioManipuladoOCuentaNoDisponibleNoEnvianNada() {
        assertThat(servicio.solicitar("manipulado", CanalMfa.EMAIL)).isEqualTo(ResultadoSolicitudOtp.Rechazo.DESAFIO_INVALIDO);
        disponible = false;
        assertThat(servicio.solicitar(token, CanalMfa.EMAIL)).isEqualTo(ResultadoSolicitudOtp.Rechazo.DESAFIO_INVALIDO);
        assertThat(servicio.verificar(token, "123456")).isEqualTo(ResultadoVerificacionOtp.Rechazo.DESAFIO_INVALIDO);
        assertThat(entregados).isEmpty();
        assertThat(eventos).isEmpty();
    }

    @Test
    void smsSinCelularEs400SinConsumirCuota() {
        assertThat(servicio.solicitar(token, CanalMfa.SMS)).isEqualTo(ResultadoSolicitudOtp.Rechazo.SIN_CELULAR);
        assertThat(memoria.otps).isEmpty();
    }

    @Test
    void enmascaraCorreoYCelular() {
        assertThat(OtpLoginService.enmascarar("cliente.a@fixtures.g7.test", CanalOtp.EMAIL)).isEqualTo("c***a@fixtures.g7.test");
        assertThat(OtpLoginService.enmascarar("987654321", CanalOtp.SMS)).isEqualTo("******321");
    }

    /** Persistencia en memoria con la misma semántica de guardado por id. */
    private static final class Memoria implements PersistenciaMfaPort {
        final Map<UUID, DesafioMfa> desafios = new HashMap<>();
        final Map<UUID, Otp> otps = new HashMap<>();

        @Override public void guardarDesafio(DesafioMfa d) { desafios.put(d.id(), d); }
        @Override public Optional<DesafioMfa> buscarDesafioPorHash(String hash) {
            return desafios.values().stream().filter(d -> d.hashToken().equals(hash)).findFirst();
        }
        @Override public Optional<DesafioMfa> bloquearDesafio(UUID id) { return Optional.ofNullable(desafios.get(id)); }
        @Override public void guardarOtp(Otp otp) { otps.put(otp.contexto().otpId(), otp); }
        @Override public Optional<Otp> bloquearOtp(UUID id) { return Optional.ofNullable(otps.get(id)); }
        @Override public List<Otp> listarCodigosDelDesafio(UUID desafioId) {
            return otps.values().stream().filter(o -> o.contexto().desafioId().equals(desafioId))
                    .sorted(Comparator.comparing(Otp::creadoEn).reversed()).toList();
        }
        @Override public long contarSolicitudes(UUID usuarioId, Instant limite) {
            return otps.values().stream().filter(o -> o.contexto().usuarioId().equals(usuarioId)
                    && o.creadoEn().isAfter(limite)).count();
        }
        @Override public Optional<Instant> primeraSolicitudEnVentana(UUID usuarioId, Instant limite) {
            return otps.values().stream().filter(o -> o.creadoEn().isAfter(limite)).map(Otp::creadoEn).min(Instant::compareTo);
        }
    }
}
