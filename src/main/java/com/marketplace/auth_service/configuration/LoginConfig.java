package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.credencial.application.port.in.CredencialesPort;
import com.marketplace.auth_service.mfa.application.port.in.MfaPort;
import com.marketplace.auth_service.mfa.application.port.in.OtpLoginPort;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import com.marketplace.auth_service.sesion.adapter.out.capacidad.PuenteLogin;
import com.marketplace.auth_service.sesion.adapter.out.transaccion.TransaccionLoginSpring;
import com.marketplace.auth_service.sesion.application.port.in.LoginPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.sesion.application.service.LoginService;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wiring del login (EP-02-T006), separado de {@link SesionConfig}: compone cinco
 * capacidades más y las pruebas de sesiones no tienen por qué cargarlas.
 */
@Configuration(proxyBeanMethods = false)
public class LoginConfig {

    @Bean
    LoginPort loginPort(CuentasPort cuentas, CredencialesPort credenciales, ProteccionPort proteccion,
                        AutorizacionPort autorizacion, MfaPort mfa, OtpLoginPort otp, SesionesPort sesiones,
                        PlatformTransactionManager transacciones, ObjectProvider<Clock> clock) {
        return new LoginService(new PuenteLogin(cuentas, credenciales, proteccion, autorizacion, mfa, otp), sesiones,
                new TransaccionLoginSpring(transacciones), clock.getIfAvailable(Clock::systemUTC));
    }
}
