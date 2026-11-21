package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.shared.adapter.web.FiltroRegistroPeticiones;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registro de peticiones (docs/operacion/LOGS.md). Va primero de todo, antes de la cadena de
 * seguridad, para medir la petición completa y registrar también los 401 y 403 del filtro de acceso.
 */
@Configuration(proxyBeanMethods = false)
public class RegistroPeticionesConfig {

    @Bean
    FilterRegistrationBean<FiltroRegistroPeticiones> registroPeticiones() {
        FilterRegistrationBean<FiltroRegistroPeticiones> registro =
                new FilterRegistrationBean<>(new FiltroRegistroPeticiones());
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registro.addUrlPatterns("/*");
        registro.setAsyncSupported(true);
        return registro;
    }
}
