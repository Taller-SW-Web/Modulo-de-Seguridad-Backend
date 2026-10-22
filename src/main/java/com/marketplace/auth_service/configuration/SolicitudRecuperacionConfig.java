package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.adapter.in.transaction.SolicitarRecuperacionTransaccional;
import com.marketplace.auth_service.credencial.adapter.out.capacidad.AuditoriaRecuperacionAdapter;
import com.marketplace.auth_service.credencial.adapter.out.capacidad.CorreoRecuperacionAdapter;
import com.marketplace.auth_service.credencial.adapter.out.capacidad.CuentasRecuperacionAdapter;
import com.marketplace.auth_service.credencial.adapter.out.crypto.HmacCorreoSolicitudDenegado;
import com.marketplace.auth_service.credencial.adapter.out.crypto.HmacCorreoSolicitudJca;
import com.marketplace.auth_service.credencial.adapter.out.persistence.SolicitudesRecuperacionPostgresAdapter;
import com.marketplace.auth_service.credencial.application.port.in.SolicitarRecuperacionPort;
import com.marketplace.auth_service.credencial.application.port.out.*;
import com.marketplace.auth_service.credencial.application.service.SolicitudRecuperacionService;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wiring de FEAT-03-T2; dependencias de cuentas, tokens, auditoría y mensajería por puertos. */
@Configuration(proxyBeanMethods = false)
public class SolicitudRecuperacionConfig {
    private static final Logger LOG = LoggerFactory.getLogger(SolicitudRecuperacionConfig.class);

    @Bean
    HashCorreoSolicitudPort hashCorreoSolicitudPort(Environment entorno) {
        String material = entorno.getProperty("AUTH_SOLICITUD_LIMITADA_HMAC_KEY");
        if (material == null || material.isBlank()) {
            LOG.warn("Recuperación: límite denegado; falta AUTH_SOLICITUD_LIMITADA_HMAC_KEY");
            return new HmacCorreoSolicitudDenegado();
        }
        byte[] clave = null;
        try {
            clave = Base64.getDecoder().decode(material);
            return new HmacCorreoSolicitudJca(clave);
        } catch (IllegalArgumentException e) {
            LOG.error("Recuperación: límite denegado; clave HMAC inválida (Base64, mínimo 32 bytes)");
            return new HmacCorreoSolicitudDenegado();
        } finally {
            if (clave != null) Arrays.fill(clave, (byte) 0);
        }
    }

    @Bean CuentasRecuperacionPort cuentasRecuperacionPort(CuentasPort cuentas) {
        return new CuentasRecuperacionAdapter(cuentas);
    }

    @Bean CuotaRecuperacionPort cuotaRecuperacionPort(JdbcTemplate jdbc, HashCorreoSolicitudPort hash) {
        return new SolicitudesRecuperacionPostgresAdapter(jdbc, hash);
    }

    @Bean CorreoRecuperacionPort correoRecuperacionPort(TokensUnUsoPort tokens, MensajeriaPort mensajeria,
            @Value("${g7.spa.url:http://localhost:5173}") String urlSpa) {
        return new CorreoRecuperacionAdapter(tokens, mensajeria, urlSpa);
    }

    @Bean AuditoriaRecuperacionPort auditoriaRecuperacionPort(AuditoriaPort auditoria) {
        return new AuditoriaRecuperacionAdapter(auditoria);
    }

    @Bean SolicitarRecuperacionPort solicitarRecuperacionPort(CuentasRecuperacionPort cuentas,
            CuotaRecuperacionPort cuota, CorreoRecuperacionPort correo, AuditoriaRecuperacionPort auditoria,
            ObjectProvider<Clock> relojes) {
        return new SolicitarRecuperacionTransaccional(new SolicitudRecuperacionService(cuentas, cuota,
                correo, auditoria, relojes.getIfAvailable(Clock::systemUTC)));
    }
}
