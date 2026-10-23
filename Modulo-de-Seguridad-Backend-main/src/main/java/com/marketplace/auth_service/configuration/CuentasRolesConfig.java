package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.autorizacion.adapter.out.capacidad.CuentasRolesAdapter;
import com.marketplace.auth_service.autorizacion.application.port.out.CuentasRolesPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Puente real de la gestión de roles hacia cuentas (Eva, EP-01-T002): bloqueo en
 * orden y conteo de activos con estado efectivo. Separado de {@link RolesConfig}
 * para que las pruebas de roles puedan componer sus propios proveedores.
 */
@Configuration(proxyBeanMethods = false)
public class CuentasRolesConfig {

    @Bean
    CuentasRolesPort cuentasRolesPort(CuentasPort cuentas) {
        return new CuentasRolesAdapter(cuentas);
    }
}
