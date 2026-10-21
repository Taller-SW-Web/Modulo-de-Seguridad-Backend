package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.identidad.adapter.in.transaction.TokenServicioTransaccional;
import com.marketplace.auth_service.identidad.adapter.out.capacidad.EstadoIdentidadAdapter;
import com.marketplace.auth_service.identidad.adapter.out.persistence.ClientesServicioJdbcAdapter;
import com.marketplace.auth_service.identidad.application.port.in.FirmaPort;
import com.marketplace.auth_service.identidad.application.port.in.IntrospeccionPort;
import com.marketplace.auth_service.identidad.application.port.in.TokenServicioPort;
import com.marketplace.auth_service.identidad.application.service.EmitirTokenServicioService;
import com.marketplace.auth_service.identidad.application.service.IntrospeccionService;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wiring de la integración con módulos consumidores (SPEC-17, FEAT-05).
 *
 * <p>La introspección no abre una transacción envolvente: cada puerto que
 * consulta (sesiones, cuentas, autorización) abre la suya. Un acceso revocado
 * llega de sesiones como rechazo que marca su transacción para revertir; si
 * compartiera una transacción común, el rechazo esperado la invalidaría entera.
 * Los proveedores se resuelven al usarse, para que los contextos parciales de
 * otras pruebas arranquen sin ellos.
 */
@Configuration(proxyBeanMethods = false)
public class IntegracionConfig {

    @Bean
    TokenServicioPort tokenServicioPort(JdbcTemplate jdbc, FirmaPort firma, PlatformTransactionManager gestor) {
        return new TokenServicioTransaccional(
                new EmitirTokenServicioService(new ClientesServicioJdbcAdapter(jdbc), firma), gestor);
    }

    @Bean
    IntrospeccionPort introspeccionPort(FirmaPort firma, ObjectProvider<SesionesPort> sesiones,
                                        ObjectProvider<CuentasPort> cuentas,
                                        ObjectProvider<AutorizacionPort> autorizacion) {
        return (solicitante, token) -> new IntrospeccionService(firma, new EstadoIdentidadAdapter(
                sesiones.getObject(), cuentas.getObject(), autorizacion.getObject()))
                .introspeccionar(solicitante, token);
    }
}
