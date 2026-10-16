package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.autorizacion.adapter.out.capacidad.EfectosRolesAdapter;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.proteccion.adapter.out.capacidad.EfectosProteccionAdapter;
import com.marketplace.auth_service.proteccion.adapter.out.capacidad.TokensProteccionAdapter;
import com.marketplace.auth_service.proteccion.application.port.out.EfectosProteccionPort;
import com.marketplace.auth_service.proteccion.application.port.out.TokensProteccionPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Puentes de efectos entre capacidades, ahora que existen auditoría, outbox, sesiones y tokens:
 * habilitan la gestión de roles (RolesConfig deja de responder 503) y el bloqueo automático
 * con su enlace de desbloqueo (ProteccionConfig). {@code G7_SPA_URL} es el origen público de
 * la SPA para el enlace del correo.
 */
@Configuration(proxyBeanMethods = false)
public class EfectosConfig {

    @Bean
    EfectosRolesAdapter efectosRoles(AuditoriaPort auditoria, MensajeriaPort mensajeria, SesionesPort sesiones) {
        return new EfectosRolesAdapter(auditoria, mensajeria, sesiones);
    }

    @Bean
    TokensProteccionPort tokensProteccion(TokensUnUsoPort tokens) {
        return new TokensProteccionAdapter(tokens);
    }

    @Bean
    EfectosProteccionPort efectosProteccion(AuditoriaPort auditoria, MensajeriaPort mensajeria,
                                            @Value("${g7.spa.url:http://localhost:5173}") String urlSpa) {
        return new EfectosProteccionAdapter(auditoria, mensajeria, urlSpa);
    }
}
