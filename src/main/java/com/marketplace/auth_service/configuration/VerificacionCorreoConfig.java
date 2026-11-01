package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.EfectosVerificacionAdapter;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.TokensVerificacionAdapter;
import com.marketplace.auth_service.usuario.adapter.out.crypto.HuellaCorreoDenegada;
import com.marketplace.auth_service.usuario.adapter.out.crypto.HuellaCorreoHmac;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.CuentasVerificacionJpaAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.CuotaReenvioPostgresAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.port.in.VerificacionCorreoPort;
import com.marketplace.auth_service.usuario.application.port.out.CorreoVerificacionPort;
import com.marketplace.auth_service.usuario.application.port.out.CuentasVerificacionPort;
import com.marketplace.auth_service.usuario.application.port.out.CuotaReenvioPort;
import com.marketplace.auth_service.usuario.application.port.out.HuellaCorreoPort;
import com.marketplace.auth_service.usuario.application.service.VerificacionCorreoService;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wiring de la verificación de correo y su reenvío (SPEC-02, FEAT-01-T3). La cuota usa la clave
 * {@code AUTH_SOLICITUD_LIMITADA_HMAC_KEY} (Base64, mínimo 32 bytes), la misma que la recuperación;
 * sin ella el reenvío responde 503 a cualquier correo.
 */
@Configuration(proxyBeanMethods = false)
public class VerificacionCorreoConfig {
    private static final Logger LOG = LoggerFactory.getLogger(VerificacionCorreoConfig.class);

    @Bean
    HuellaCorreoPort huellaCorreoPort(Environment entorno) {
        String material = entorno.getProperty("AUTH_SOLICITUD_LIMITADA_HMAC_KEY");
        if (material == null || material.isBlank()) {
            LOG.warn("Reenvío de verificación denegado: falta AUTH_SOLICITUD_LIMITADA_HMAC_KEY");
            return new HuellaCorreoDenegada();
        }
        byte[] clave = null;
        try {
            clave = Base64.getDecoder().decode(material.strip());
            return new HuellaCorreoHmac(clave);
        } catch (IllegalArgumentException invalida) {
            LOG.error("Reenvío de verificación denegado: clave HMAC inválida (Base64, mínimo 32 bytes)");
            return new HuellaCorreoDenegada();
        } finally {
            if (clave != null) Arrays.fill(clave, (byte) 0);
        }
    }

    @Bean
    CuotaReenvioPort cuotaReenvioPort(JdbcTemplate jdbc, HuellaCorreoPort huellas) {
        return new CuotaReenvioPostgresAdapter(jdbc, huellas);
    }

    @Bean
    CuentasVerificacionPort cuentasVerificacionPort(UsuarioJpaRepository usuarios) {
        return new CuentasVerificacionJpaAdapter(usuarios);
    }

    @Bean
    VerificacionCorreoPort verificacionCorreoPort(TokensUnUsoPort tokens, CuentasVerificacionPort cuentas,
                                                  CuotaReenvioPort cuota, CorreoVerificacionPort correoVerificacion,
                                                  AutorizacionPort autorizacion, MensajeriaPort mensajeria,
                                                  AuditoriaPort auditoria, PlatformTransactionManager transacciones,
                                                  ObjectProvider<Clock> reloj) {
        Clock clock = reloj.getIfAvailable(Clock::systemUTC);
        return new VerificacionCorreoService(new TokensVerificacionAdapter(tokens), cuentas, cuota, correoVerificacion,
                new EfectosVerificacionAdapter(autorizacion, mensajeria, auditoria, transacciones, clock), clock);
    }
}
