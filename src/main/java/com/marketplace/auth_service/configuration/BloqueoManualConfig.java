package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.proteccion.adapter.in.transaction.BloqueoManualTransaccional;
import com.marketplace.auth_service.proteccion.adapter.out.capacidad.OperacionManualAdapter;
import com.marketplace.auth_service.proteccion.application.port.in.BloqueoManualPort;
import com.marketplace.auth_service.proteccion.application.port.out.*;
import com.marketplace.auth_service.proteccion.application.service.BloqueoManualService;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.usuario.application.port.in.CuentasPort;
import com.marketplace.auth_service.sesion.application.port.in.SesionesPort;
import com.marketplace.auth_service.auditoria.application.port.in.*;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class BloqueoManualConfig {
    @Bean BloqueoManualPort bloqueoManualPort(CuentasProteccionPort cuentas, PersistenciaProteccionPort persistencia,
            TokensProteccionPort tokens, HuellaProteccionPort huellas, AutorizacionPort roles, CuentasPort identidades,
            SesionesPort sesiones, AuditoriaPort auditoria, MensajeriaPort mensajes, ObjectProvider<Clock> reloj) {
        return new BloqueoManualTransaccional(new BloqueoManualService(cuentas,persistencia,tokens,huellas,
                new OperacionManualAdapter(roles,identidades,sesiones,auditoria,mensajes),reloj.getIfAvailable(Clock::systemUTC)));
    }
}
