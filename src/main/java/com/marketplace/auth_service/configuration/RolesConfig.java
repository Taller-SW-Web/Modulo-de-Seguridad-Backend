package com.marketplace.auth_service.configuration;

import com.marketplace.auth_service.autorizacion.adapter.in.transaction.GestionRolesTransaccional;
import com.marketplace.auth_service.autorizacion.application.port.in.AutorizacionPort;
import com.marketplace.auth_service.autorizacion.application.port.in.GestionRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AsociacionesRolPort;
import com.marketplace.auth_service.autorizacion.application.port.out.AuditoriaRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.CuentasRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.EventosRolesPort;
import com.marketplace.auth_service.autorizacion.application.port.out.SesionesRolesPort;
import com.marketplace.auth_service.autorizacion.application.service.GestionRolesNoDisponible;
import com.marketplace.auth_service.autorizacion.application.service.GestionRolesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Wiring de la gestión de roles (EP-05-T003).
 *
 * <p>El cambio de rol necesita cuatro proveedores de otros dueños, que llegan
 * como puentes en {@code autorizacion.adapter.out.capacidad}:
 * {@link CuentasRolesPort} (Eva), {@link SesionesRolesPort} (Jose Luis),
 * {@link AuditoriaRolesPort} y {@link EventosRolesPort} (Christian). Si falta
 * alguno, el bean es {@link GestionRolesNoDisponible}: la aplicación arranca,
 * el permiso se sigue comprobando y el cambio responde 503, nunca un cambio
 * sin sus efectos. El arranque deja un aviso con lo que falta.
 */
@Configuration(proxyBeanMethods = false)
public class RolesConfig {

    private static final Logger log = LoggerFactory.getLogger(RolesConfig.class);

    @Bean
    GestionRolesPort gestionRolesPort(AutorizacionPort autorizacion, AsociacionesRolPort asociaciones,
                                      ObjectProvider<CuentasRolesPort> cuentas,
                                      ObjectProvider<SesionesRolesPort> sesiones,
                                      ObjectProvider<AuditoriaRolesPort> auditoria,
                                      ObjectProvider<EventosRolesPort> eventos,
                                      ObjectProvider<Clock> clock,
                                      PlatformTransactionManager transacciones) {
        CuentasRolesPort cuentasPort = cuentas.getIfAvailable();
        SesionesRolesPort sesionesPort = sesiones.getIfAvailable();
        AuditoriaRolesPort auditoriaPort = auditoria.getIfAvailable();
        EventosRolesPort eventosPort = eventos.getIfAvailable();

        List<String> faltantes = new ArrayList<>();
        if (cuentasPort == null) {
            faltantes.add("CuentasRolesPort (cuentas, Eva)");
        }
        if (sesionesPort == null) {
            faltantes.add("SesionesRolesPort (sesiones, Jose Luis)");
        }
        if (auditoriaPort == null) {
            faltantes.add("AuditoriaRolesPort (auditoría, Christian)");
        }
        if (eventosPort == null) {
            faltantes.add("EventosRolesPort (outbox, Christian)");
        }
        if (!faltantes.isEmpty()) {
            log.warn("Gestión de roles no disponible (responde 503): faltan {}", faltantes);
            return new GestionRolesNoDisponible(autorizacion, faltantes);
        }

        Clock reloj = clock.getIfAvailable(Clock::systemUTC);
        return new GestionRolesTransaccional(
                new GestionRolesService(autorizacion, asociaciones, cuentasPort, sesionesPort, auditoriaPort,
                        eventosPort, reloj),
                transacciones, auditoriaPort, reloj);
    }
}
