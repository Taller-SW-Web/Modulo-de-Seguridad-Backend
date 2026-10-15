package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.proteccion.adapter.out.crypto.HuellaProteccionJca;
import com.marketplace.auth_service.proteccion.application.port.out.HuellaProteccionPort;
import com.marketplace.auth_service.proteccion.application.service.HuellaProteccionDenegada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class ProteccionCriptografiaConfig {
    private static final Logger LOG = LoggerFactory.getLogger(ProteccionCriptografiaConfig.class);
    @Bean
    HuellaProteccionPort huellaProteccionPort(Environment entorno) {
        String id = entorno.getProperty("AUTH_FALLOS_HMAC_KEY_ID");
        String material = entorno.getProperty("AUTH_FALLOS_HMAC_KEY");
        if (id == null || id.isBlank() || material == null || material.isBlank()) {
            LOG.warn("Protección: huellas denegadas; falta configuración de clave externa");
            return new HuellaProteccionDenegada();
        }
        byte[] clave = null;
        try {
            clave = Base64.getDecoder().decode(material);
            return new HuellaProteccionJca(id, Map.of(id, clave));
        } catch (IllegalArgumentException e) {
            LOG.error("Protección: huellas denegadas; configuración de clave externa inválida");
            return new HuellaProteccionDenegada();
        } finally {
            if (clave != null) Arrays.fill(clave, (byte) 0);
        }
    }
}
