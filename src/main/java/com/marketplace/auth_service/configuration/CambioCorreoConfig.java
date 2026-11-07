package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.in.TokensUnUsoPort;
import com.marketplace.auth_service.perfil.adapter.out.capacidad.CredencialesCorreoAdapter;
import com.marketplace.auth_service.perfil.adapter.out.capacidad.CuentaCorreoAdapter;
import com.marketplace.auth_service.perfil.adapter.out.capacidad.EfectosCambioCorreoAdapter;
import com.marketplace.auth_service.perfil.adapter.out.capacidad.NotificacionesCorreoAdapter;
import com.marketplace.auth_service.perfil.application.port.in.CambioCorreoPort;
import com.marketplace.auth_service.perfil.application.service.CambioCorreoService;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Wiring de la solicitud de cambio de correo (SPEC-16, FEAT-04-T3). {@code g7.spa.url} arma el enlace. */
@Configuration(proxyBeanMethods = false)
public class CambioCorreoConfig {

    @Bean
    CambioCorreoPort cambioCorreoPort(CredencialesPort credenciales, CuentasPort cuentas, TokensUnUsoPort tokens,
                                      MensajeriaPort mensajeria, PlatformTransactionManager transacciones,
                                      ObjectProvider<Clock> reloj, @Value("${g7.spa.url:http://localhost:5173}") String urlSpa) {
        return new CambioCorreoService(new CredencialesCorreoAdapter(credenciales), new CuentaCorreoAdapter(cuentas),
                new NotificacionesCorreoAdapter(tokens, mensajeria, urlSpa, reloj.getIfAvailable(Clock::systemUTC)),
                new EfectosCambioCorreoAdapter(transacciones));
    }
}
