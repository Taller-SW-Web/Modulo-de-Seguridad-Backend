package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.identidad.adapter.in.transaction.ClientesTecnicosTransaccional;
import com.marketplace.auth_service.identidad.adapter.out.capacidad.GobiernoClientesAdapter;
import com.marketplace.auth_service.identidad.adapter.out.persistence.RegistroClientesJdbcAdapter;
import com.marketplace.auth_service.identidad.application.port.in.ClientesTecnicosPort;
import com.marketplace.auth_service.identidad.application.service.GestionClientesTecnicosService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

/** Gestión de clientes técnicos (SPEC-19, FEAT-14). */
@Configuration(proxyBeanMethods = false)
public class ClientesTecnicosConfig {

    @Bean
    ClientesTecnicosPort clientesTecnicosPort(JdbcTemplate jdbc, AutorizacionPort autorizacion,
                                              AuditoriaPort auditoria, ObjectProvider<Clock> reloj) {
        return new ClientesTecnicosTransaccional(new GestionClientesTecnicosService(
                new RegistroClientesJdbcAdapter(jdbc), new GobiernoClientesAdapter(autorizacion, auditoria),
                reloj.getIfAvailable(Clock::systemUTC)));
    }
}
