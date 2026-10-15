package com.marketplace.auth_service.mfa.adapter.out.crypto;

import com.marketplace.auth_service.mfa.domain.model.ContextoOtp;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CriptografiaMfaJcaTest {
    private static final ContextoOtp CONTEXTO = new ContextoOtp(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    private static byte[] clave(int valor) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) valor);
        return bytes;
    }

    private static CriptografiaMfaJca conCodigo(int codigo) {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(codigo);
        return new CriptografiaMfaJca("otp-test", Map.of("otp-test", clave(1)), random);
    }

    @Test
    void desafioTiene32BytesAleatoriosYHashReproducible() {
        var crypto = new CriptografiaMfaJca("otp-test", Map.of("otp-test", clave(1)), new SecureRandom());
        var primero = crypto.generarDesafio();
        var segundo = crypto.generarDesafio();

        assertThat(Base64.getUrlDecoder().decode(primero.token())).hasSize(32);
        assertThat(primero.token()).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(primero.hashToken()).matches("[a-f0-9]{64}").isEqualTo(crypto.hashDesafio(primero.token()));
        assertThat(primero.token()).isNotEqualTo(segundo.token());
        assertThat(primero.hashToken()).isNotEqualTo(segundo.hashToken());
    }

    @Test
    void desafioMalformadoSeRechazaSinExponerlo() {
        var crypto = conCodigo(0);
        for (String token : new String[]{"secreto", " ", "A".repeat(42), "A".repeat(42) + "B"}) {
            assertThatThrownBy(() -> crypto.hashDesafio(token))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Formato de desafío inválido");
        }
        assertThatThrownBy(() -> crypto.hashDesafio(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otpConservaLosCerosIniciales() {
        assertThat(conCodigo(42).generarOtp(CONTEXTO).codigo()).isEqualTo("000042");
        assertThat(conCodigo(0).generarOtp(CONTEXTO).codigo()).isEqualTo("000000");
        assertThat(conCodigo(999_999).generarOtp(CONTEXTO).codigo()).isEqualTo("999999");
    }

    @Test
    void hashSoloVerificaElCodigoCorrectoDelMismoContexto() {
        var crypto = conCodigo(123_456);
        var otp = crypto.generarOtp(CONTEXTO);
        assertThat(otp.hashCodigo()).matches("[a-f0-9]{64}").isNotEqualTo(otp.codigo());
        assertThat(crypto.coincideOtp(CONTEXTO, otp.codigo(), otp.hashCodigo(), otp.hashKeyId())).isTrue();
        assertThat(crypto.coincideOtp(CONTEXTO, "654321", otp.hashCodigo(), otp.hashKeyId())).isFalse();
        var otroUsuario = new ContextoOtp(UUID.randomUUID(), CONTEXTO.desafioId(), CONTEXTO.otpId());
        var otroDesafio = new ContextoOtp(CONTEXTO.usuarioId(), UUID.randomUUID(), CONTEXTO.otpId());
        var otroOtp = new ContextoOtp(CONTEXTO.usuarioId(), CONTEXTO.desafioId(), UUID.randomUUID());
        for (var otro : new ContextoOtp[]{otroUsuario, otroDesafio, otroOtp}) {
            assertThat(crypto.coincideOtp(otro, otp.codigo(), otp.hashCodigo(), otp.hashKeyId())).isFalse();
        }
    }

    @Test
    void noNormalizaNiAceptaDigitosDeOtroAlfabeto() {
        var crypto = conCodigo(42);
        var otp = crypto.generarOtp(CONTEXTO);
        for (String codigo : new String[]{"42", " 000042", "000042 ", "00004x", "００００４２"}) {
            assertThat(crypto.coincideOtp(CONTEXTO, codigo, otp.hashCodigo(), otp.hashKeyId())).isFalse();
        }
        assertThat(crypto.coincideOtp(CONTEXTO, null, otp.hashCodigo(), otp.hashKeyId())).isFalse();
    }

    @Test
    void reinicioConLaMismaClavePermiteVerificar() {
        var primero = conCodigo(123_456);
        var otp = primero.generarOtp(CONTEXTO);
        var reiniciado = new CriptografiaMfaJca("otp-test", Map.of("otp-test", clave(1)), new SecureRandom());
        assertThat(reiniciado.coincideOtp(CONTEXTO, otp.codigo(), otp.hashCodigo(), otp.hashKeyId())).isTrue();
    }

    @Test
    void unaClaveDistintaODesechadaNoVerifica() {
        var otp = conCodigo(123_456).generarOtp(CONTEXTO);
        var distinta = new CriptografiaMfaJca("otp-test", Map.of("otp-test", clave(2)), new SecureRandom());
        assertThat(distinta.coincideOtp(CONTEXTO, otp.codigo(), otp.hashCodigo(), otp.hashKeyId())).isFalse();
        assertThat(distinta.coincideOtp(CONTEXTO, otp.codigo(), otp.hashCodigo(), "desconocida")).isFalse();
        assertThat(distinta.coincideOtp(CONTEXTO, otp.codigo(), otp.hashCodigo(), null)).isFalse();
    }

    @Test
    void mantieneLaClaveHistoricaCuandoSeConfiguraUnaNueva() {
        var otp = conCodigo(123_456).generarOtp(CONTEXTO);
        var nueva = new CriptografiaMfaJca("nueva", Map.of("nueva", clave(2), "otp-test", clave(1)), new SecureRandom());
        assertThat(nueva.coincideOtp(CONTEXTO, otp.codigo(), otp.hashCodigo(), otp.hashKeyId())).isTrue();
        assertThat(nueva.generarOtp(CONTEXTO).hashKeyId()).isEqualTo("nueva");
    }

    @Test
    void noAutogeneraClaveCuandoFaltaOMideMenosDe32Bytes() {
        assertThatThrownBy(() -> new CriptografiaMfaJca("otp-test", Map.of(), new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CriptografiaMfaJca("otp-test", Map.of("otp-test", new byte[31]), new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CriptografiaMfaJca("no-existe", Map.of("otp-test", clave(1)), new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cambiarElArrayExternoNoCambiaLaClaveCargada() {
        byte[] externa = clave(1);
        var crypto = new CriptografiaMfaJca("otp-test", Map.of("otp-test", externa), new SecureRandom());
        var otp = crypto.generarOtp(CONTEXTO);
        Arrays.fill(externa, (byte) 2);
        assertThat(crypto.coincideOtp(CONTEXTO, otp.codigo(), otp.hashCodigo(), otp.hashKeyId())).isTrue();
    }

    @Test
    void unHashMalformadoSeRechaza() {
        var crypto = conCodigo(123_456);
        for (String hash : new String[]{"", "a".repeat(63), "g".repeat(64)}) {
            assertThat(crypto.coincideOtp(CONTEXTO, "123456", hash, "otp-test")).isFalse();
        }
        assertThat(crypto.coincideOtp(CONTEXTO, "123456", null, "otp-test")).isFalse();
    }

    @Test
    void representacionesNoExponenCodigosTokensNiHashes() {
        var crypto = conCodigo(123_456);
        var desafio = crypto.generarDesafio();
        var otp = crypto.generarOtp(CONTEXTO);
        assertThat(desafio.toString()).doesNotContain(desafio.token(), desafio.hashToken());
        assertThat(otp.toString()).doesNotContain(otp.codigo(), otp.hashCodigo());
        assertThat(crypto.toString()).contains("REDACTADO");
    }
}
