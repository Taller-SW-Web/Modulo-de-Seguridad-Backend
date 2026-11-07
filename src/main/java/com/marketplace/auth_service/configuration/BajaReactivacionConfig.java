package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.usuario.adapter.in.transaction.BajaReactivacionTransaccional;
import com.marketplace.auth_service.usuario.adapter.out.capacidad.EfectosBajaReactivacionAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.adapter.EstadoCuentaJpaAdapter;
import com.marketplace.auth_service.usuario.adapter.out.persistence.repository.UsuarioJpaRepository;
import com.marketplace.auth_service.usuario.application.port.in.BajaReactivacionPort;
import com.marketplace.auth_service.usuario.application.port.out.EstadoCuentaPort;
import com.marketplace.auth_service.usuario.application.port.out.UsuarioRepositoryPort;
import com.marketplace.auth_service.usuario.application.service.BajaReactivacionService;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring de la baja y la reactivación (SPEC-04, FEAT-02), aparte de UsuarioConfiguration. */
@Configuration(proxyBeanMethods = false)
public class BajaReactivacionConfig {

    @Bean
    EstadoCuentaPort estadoCuentaPort(UsuarioJpaRepository usuarios) {
        return new EstadoCuentaJpaAdapter(usuarios);
    }

    @Bean
    BajaReactivacionPort bajaReactivacionPort(UsuarioRepositoryPort cuentas, EstadoCuentaPort estados,
                                              AutorizacionPort autorizacion, SesionesPort sesiones,
                                              AuditoriaPort auditoria, MensajeriaPort mensajeria,
                                              ObjectProvider<Clock> reloj) {
        return new BajaReactivacionTransaccional(new BajaReactivacionService(cuentas, estados,
                new EfectosBajaReactivacionAdapter(autorizacion, sesiones, auditoria, mensajeria),
                reloj.getIfAvailable(Clock::systemUTC)));
    }
}
