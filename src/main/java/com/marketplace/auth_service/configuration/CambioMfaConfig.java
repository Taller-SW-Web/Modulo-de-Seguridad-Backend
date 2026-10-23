package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.auditoria.application.port.in.AuditoriaPort;
import com.marketplace.auth_service.auditoria.application.port.in.MensajeriaPort;
import com.marketplace.auth_service.mfa.adapter.in.transaction.CambioMfaTransaccional;
import com.marketplace.auth_service.mfa.adapter.out.capacidad.EfectosCambioMfaAdapter;
import com.marketplace.auth_service.mfa.application.port.in.CambioMfaPort;
import com.marketplace.auth_service.mfa.application.port.out.*;
import com.marketplace.auth_service.mfa.application.service.CambioMfaService;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentaEscrituraInterna;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class CambioMfaConfig {
    @Bean CambioMfaPort cambioMfaPort(CuentasMfaPort cuentas, RolesMfaPort roles, PersistenciaCambioMfaPort cambios,
            PersistenciaMfaPort otp, CriptografiaMfaPort cripto, SesionesPort sesiones, CuentaEscrituraInterna escritura,
            MensajeriaPort mensajes, AuditoriaPort auditoria, ObjectProvider<Clock> reloj, PlatformTransactionManager gestor) {
        return new CambioMfaTransaccional(new CambioMfaService(cuentas, roles, cambios, otp, cripto,
                new EfectosCambioMfaAdapter(sesiones, escritura, mensajes, auditoria), reloj.getIfAvailable(Clock::systemUTC)), gestor);
    }
}
