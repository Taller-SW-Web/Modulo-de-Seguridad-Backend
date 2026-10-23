package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.autorizacion.adapter.in.transaction.AutorizacionTransaccional;
import com.marketplace.auth_service.autorizacion.adapter.in.transaction.CatalogoTransaccional;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.AutorizacionJpaAdapter;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.CatalogoLecturaJpaAdapter;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.CatalogoJpaRepository;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.PermisoJpaRepository;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.RolPermisoJpaRepository;
import com.marketplace.auth_service.autorizacion.adapter.out.persistence.repository.UsuarioRolJpaRepository;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.in.CatalogoPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AsociacionesRolPort;
import com.marketplace.auth_service.autorizacion.application.service.AutorizacionService;
import com.marketplace.auth_service.autorizacion.application.service.CatalogoService;
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
    AsociacionesRolPort asociacionesRolPort(UsuarioRolJpaRepository usuarioRol, RolPermisoJpaRepository rolPermiso,
                                            CatalogoJpaRepository catalogo) {
        return new AutorizacionJpaAdapter(usuarioRol, rolPermiso, catalogo);
    }

    @Bean
    AutorizacionPort autorizacionPort(AsociacionesRolPort asociaciones) {
        return new AutorizacionTransaccional(new AutorizacionService(asociaciones));
    }

    /** Catálogo para consumidores (SPEC-18 ESC-18.7), detrás de su decorador de solo lectura. */
    @Bean
    CatalogoPort catalogoPort(CatalogoJpaRepository catalogo, RolPermisoJpaRepository rolPermiso,
                              PermisoJpaRepository permisos) {
        return new CatalogoTransaccional(new CatalogoService(new CatalogoLecturaJpaAdapter(catalogo, rolPermiso, permisos)));
    }
}
