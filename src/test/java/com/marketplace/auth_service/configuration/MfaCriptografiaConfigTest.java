package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.mfa.application.port.out.CriptografiaMfaPort;
import com.marketplace.auth_service.mfa.application.service.CriptografiaMfaDenegada;
import com.marketplace.auth_service.mfa.domain.exception.CriptografiaMfaNoDisponibleException;
import com.marketplace.auth_service.mfa.domain.model.ContextoOtp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaCriptografiaConfigTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(MfaCriptografiaConfig.class);
    private static final String CLAVE_PRUEBA = Base64.getEncoder().encodeToString(new byte[32]);
    private static final ContextoOtp DATOS = new ContextoOtp(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Test void configuracionValidaProtegeYVerificaOtp() {
        contexto.withPropertyValues("AUTH_OTP_HMAC_KEY_ID=otp-test", "AUTH_OTP_HMAC_KEY=" + CLAVE_PRUEBA)
                .run(ctx -> {
                    var crypto = ctx.getBean(CriptografiaMfaPort.class);
                    var otp = crypto.generarOtp(DATOS);
                    assertThat(crypto.coincideOtp(DATOS, otp.codigo(), otp.hashCodigo(), otp.hashKeyId())).isTrue();
                });
    }

    @Test void sinClaveArrancaPeroDeniegaTodaCriptografia() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var crypto = ctx.getBean(CriptografiaMfaPort.class);
            assertThat(crypto).isInstanceOf(CriptografiaMfaDenegada.class);
            assertThatThrownBy(crypto::generarDesafio).isInstanceOf(CriptografiaMfaNoDisponibleException.class);
            assertThatThrownBy(() -> crypto.generarOtp(DATOS)).isInstanceOf(CriptografiaMfaNoDisponibleException.class);
            assertThatThrownBy(() -> crypto.hashDesafio("secreto")).isInstanceOf(CriptografiaMfaNoDisponibleException.class);
            assertThatThrownBy(() -> crypto.coincideOtp(DATOS, "123456", "hash", "otp-test"))
                    .isInstanceOf(CriptografiaMfaNoDisponibleException.class);
        });
    }

    @Test void faltaDeIdDeniegaAunqueExistaClave() {
        contexto.withPropertyValues("AUTH_OTP_HMAC_KEY=" + CLAVE_PRUEBA)
                .run(ctx -> assertThat(ctx.getBean(CriptografiaMfaPort.class)).isInstanceOf(CriptografiaMfaDenegada.class));
    }

    @Test void claveMalformadaOCortaDeniegaSinExponerla() {
        for (String clave : new String[]{"%no-base64%", Base64.getEncoder().encodeToString(new byte[31])}) {
            contexto.withPropertyValues("AUTH_OTP_HMAC_KEY_ID=otp-test", "AUTH_OTP_HMAC_KEY=" + clave)
                    .run(ctx -> assertThatThrownBy(() -> ctx.getBean(CriptografiaMfaPort.class).generarOtp(DATOS))
                            .isInstanceOf(CriptografiaMfaNoDisponibleException.class).hasMessageNotContaining(clave));
        }
    }

    @Test void reinicioConConfiguracionPersistenteConservaLaVerificacion() {
        contexto.withPropertyValues("AUTH_OTP_HMAC_KEY_ID=otp-test", "AUTH_OTP_HMAC_KEY=" + CLAVE_PRUEBA)
                .run(primero -> {
                    var otp = primero.getBean(CriptografiaMfaPort.class).generarOtp(DATOS);
                    contexto.withPropertyValues("AUTH_OTP_HMAC_KEY_ID=otp-test", "AUTH_OTP_HMAC_KEY=" + CLAVE_PRUEBA)
                            .run(segundo -> assertThat(segundo.getBean(CriptografiaMfaPort.class)
                                    .coincideOtp(DATOS, otp.codigo(), otp.hashCodigo(), otp.hashKeyId())).isTrue());
                });
    }
}
