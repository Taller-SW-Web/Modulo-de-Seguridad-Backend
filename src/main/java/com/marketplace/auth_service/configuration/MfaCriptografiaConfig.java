package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.mfa.adapter.out.crypto.CriptografiaMfaJca;
import com.marketplace.auth_service.mfa.application.port.out.CriptografiaMfaPort;
import com.marketplace.auth_service.mfa.application.service.CriptografiaMfaDenegada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class MfaCriptografiaConfig {
    private static final Logger LOG = LoggerFactory.getLogger(MfaCriptografiaConfig.class);

    @Bean
    CriptografiaMfaPort criptografiaMfaPort(Environment entorno) {
        String id = entorno.getProperty("AUTH_OTP_HMAC_KEY_ID");
        String material = entorno.getProperty("AUTH_OTP_HMAC_KEY");
        if (id == null || id.isBlank() || material == null || material.isBlank()) {
            LOG.warn("MFA: generación denegada; faltan AUTH_OTP_HMAC_KEY_ID/AUTH_OTP_HMAC_KEY");
            return new CriptografiaMfaDenegada();
        }
        try {
            byte[] clave = Base64.getDecoder().decode(material);
            return new CriptografiaMfaJca(id, Map.of(id, clave), new SecureRandom());
        } catch (IllegalArgumentException e) {
            LOG.error("MFA: generación denegada; configuración HMAC inválida (Base64, mínimo 32 bytes, id hasta 50 caracteres)");
            return new CriptografiaMfaDenegada();
        }
    }
}
