package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.shared.adapter.web.FiltroRegistroPeticiones;
import com.marketplace.auth_service.shared.adapter.web.PlantillasRutas;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Registro de peticiones (docs/operacion/LOGS.md). Va primero de todo, antes de la cadena de
 * seguridad, para medir la petición completa y registrar también los 401 y 403 del filtro de acceso.
 */
@Configuration(proxyBeanMethods = false)
public class RegistroPeticionesConfig {

    @Bean
    FilterRegistrationBean<FiltroRegistroPeticiones> registroPeticiones(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> rutas) {
        // Las rutas declaradas sirven para nombrar la petición cuando Spring no eligió controlador.
        RequestMappingHandlerMapping declaradas = rutas.getIfAvailable();
        FiltroRegistroPeticiones filtro = declaradas == null
                ? new FiltroRegistroPeticiones() : new FiltroRegistroPeticiones(new PlantillasRutas(declaradas));
        FilterRegistrationBean<FiltroRegistroPeticiones> registro = new FilterRegistrationBean<>(filtro);
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registro.addUrlPatterns("/*");
        registro.setAsyncSupported(true);
        return registro;
    }
}
