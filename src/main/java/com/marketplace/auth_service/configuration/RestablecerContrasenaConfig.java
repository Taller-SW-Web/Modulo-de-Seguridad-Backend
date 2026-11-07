package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.credencial.adapter.out.capacidad.EfectosRestablecimientoAdapter;
import com.marketplace.auth_service.credencial.application.port.in.*;
import com.marketplace.auth_service.credencial.application.port.out.EscrituraCredencialPort;
import com.marketplace.auth_service.credencial.application.port.out.PasswordHasherPort;
import com.marketplace.auth_service.credencial.application.service.RestablecerContrasenaService;
import com.marketplace.auth_service.proteccion.application.port.in.ProteccionPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods=false)
public class RestablecerContrasenaConfig {
    @Bean RestablecerContrasenaPort restablecerContrasenaPort(TokensUnUsoPort tokens,
            CredencialesPort credenciales, PasswordHasherPort hasher, EscrituraCredencialPort escritura,
            CuentasPort cuentas, ProteccionPort proteccion, SesionesPort sesiones,
            AuditoriaPort auditoria, MensajeriaPort mensajeria,
            PlatformTransactionManager transacciones, ObjectProvider<Clock> reloj) {
        return new RestablecerContrasenaService(tokens,credenciales,hasher,escritura,
                new EfectosRestablecimientoAdapter(cuentas,proteccion,sesiones,auditoria,mensajeria,transacciones),
                reloj.getIfAvailable(Clock::systemUTC));
    }
}
