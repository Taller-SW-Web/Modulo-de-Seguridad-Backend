package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.adapter.out.capacidad.EfectosCambioContrasenaAdapter;
import com.marketplace.auth_service.credencial.application.port.in.CambioContrasenaPort;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.credencial.application.port.out.EscrituraCredencialPort;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.credencial.application.service.CambioContrasenaService;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Wiring del cambio de contraseña (EP-03-T005), aparte de CredencialesConfig para no cargarlo en sus pruebas. */
@Configuration(proxyBeanMethods = false)
public class CambioContrasenaConfig {

    @Bean
    CambioContrasenaPort cambioContrasenaPort(CredencialesPort credenciales, PasswordHasherPort hasher,
                                              EscrituraCredencialPort escritura, CuentasPort cuentas,
                                              SesionesPort sesiones, AuditoriaPort auditoria, MensajeriaPort mensajeria,
                                              PlatformTransactionManager transacciones, ObjectProvider<Clock> reloj) {
        Clock clock = reloj.getIfAvailable(Clock::systemUTC);
        return new CambioContrasenaService(credenciales, hasher, escritura,
                new EfectosCambioContrasenaAdapter(cuentas, sesiones, auditoria, mensajeria, transacciones, clock), clock);
    }
}
