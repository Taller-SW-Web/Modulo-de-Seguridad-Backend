package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.autorizacion.adapter.in.transaction.AutorizacionTransaccional;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.AutorizacionJpaAdapter;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.RolPermisoJpaRepository;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AsociacionesRolPort;
import com.marketplace.auth_service.autorizacion.application.service.AutorizacionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring de la capacidad de autorización. El caso de uso puro se crea aquí
 * dentro de su decorador transaccional, así {@link AutorizacionPort} tiene un
 * único bean y nadie llama al servicio saltándose la transacción.
 */
@Configuration(proxyBeanMethods = false)
public class AutorizacionConfig {

    @Bean
    AsociacionesRolPort asociacionesRolPort(UsuarioRolJpaRepository usuarioRol, RolPermisoJpaRepository rolPermiso) {
        return new AutorizacionJpaAdapter(usuarioRol, rolPermiso);
    }

    @Bean
    AutorizacionPort autorizacionPort(AsociacionesRolPort asociaciones) {
        return new AutorizacionTransaccional(new AutorizacionService(asociaciones));
    }
}
